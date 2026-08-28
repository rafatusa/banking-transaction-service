# nginx as the public entrypoint, proxying to the application on loopback.
#
# The application never listens publicly: it binds 127.0.0.1 and nginx owns
# ports 80/443. That is the hardened-web-entrypoint requirement.
class banking::nginx (
  Integer $app_port     = lookup('banking::app_port'),
  String  $server_name  = lookup('banking::server_name'),
  String  $max_body     = lookup('banking::nginx_client_max_body_size'),
) {

  package { 'nginx':
    ensure => installed,
  }

  # The stock default site answers on port 80 and would shadow the app vhost
  # on plain IP access.
  file { '/etc/nginx/sites-enabled/default':
    ensure  => absent,
    require => Package['nginx'],
    notify  => Exec['nginx-config-test'],
  }

  file { '/etc/nginx/sites-available/banking.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('banking/nginx-site.conf.epp', {
      'app_port'    => $app_port,
      'server_name' => $server_name,
      'max_body'    => $max_body,
    }),
    require => Package['nginx'],
    notify  => Exec['nginx-config-test'],
  }

  file { '/etc/nginx/sites-enabled/banking.conf':
    ensure  => link,
    target  => '/etc/nginx/sites-available/banking.conf',
    require => File['/etc/nginx/sites-available/banking.conf'],
    notify  => Exec['nginx-config-test'],
  }

  # Validate before reloading: a bad config that reaches `systemctl reload`
  # takes the site down, whereas `nginx -t` fails the deploy safely.
  exec { 'nginx-config-test':
    command     => '/usr/sbin/nginx -t',
    refreshonly => true,
    logoutput   => on_failure,
    notify      => Service['nginx'],
  }

  service { 'nginx':
    ensure  => running,
    enable  => true,
    require => Package['nginx'],
  }
}
