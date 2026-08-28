# Log rotation and CloudWatch shipping.
#
# Unrotated logs filling the root volume is the most common silent way a small
# VM deployment dies, so rotation ships with the first deploy rather than being
# a follow-up task.
class banking::logging (
  String $log_dir       = lookup('banking::log_dir'),
  String $app_log_group = lookup('banking::app_log_group'),
  String $sys_log_group = lookup('banking::system_log_group'),
  String $region        = lookup('banking::region'),
) {

  file { '/etc/logrotate.d/banking-nginx':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @("ROTATE"/L),
      # Managed by Puppet.
      /var/log/nginx/banking-*.log {
          daily
          rotate 14
          missingok
          notifempty
          compress
          delaycompress
          create 0640 www-data adm
          sharedscripts
          postrotate
              [ -f /var/run/nginx.pid ] && kill -USR1 `cat /var/run/nginx.pid`
          endscript
      }
      | ROTATE
  }

  # The CloudWatch agent ships journald (application container logs) and nginx
  # access logs to the log groups terraform created.
  exec { 'download-cloudwatch-agent':
    command => '/usr/bin/curl -fsSL -o /tmp/amazon-cloudwatch-agent.deb https://amazoncloudwatch-agent.s3.amazonaws.com/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb',
    creates => '/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl',
    timeout => 300,
    tries   => 3,
  }

  package { 'amazon-cloudwatch-agent':
    ensure   => installed,
    provider => dpkg,
    source   => '/tmp/amazon-cloudwatch-agent.deb',
    require  => Exec['download-cloudwatch-agent'],
  }

  file { '/opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('banking/cloudwatch-config.json.epp', {
      'app_log_group' => $app_log_group,
      'sys_log_group' => $sys_log_group,
      'region'        => $region,
    }),
    require => Package['amazon-cloudwatch-agent'],
    notify  => Exec['restart-cloudwatch-agent'],
  }

  exec { 'restart-cloudwatch-agent':
    command     => '/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json',
    refreshonly => true,
    logoutput   => on_failure,
    timeout     => 180,
  }
}
