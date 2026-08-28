# Installs Docker Engine from Docker's official apt repository.
#
# Written with built-in resource types and guarded execs so a second apply is a
# no-op (the `creates` and `unless` guards are what make this idempotent).
class banking::docker (
  String $app_user = lookup('banking::app_user'),
) {

  file { '/etc/apt/keyrings':
    ensure => directory,
    owner  => 'root',
    group  => 'root',
    mode   => '0755',
  }

  exec { 'docker-gpg-key':
    command => '/bin/bash -c "curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg"',
    creates => '/etc/apt/keyrings/docker.gpg',
    require => File['/etc/apt/keyrings'],
    timeout => 120,
  }

  file { '/etc/apt/keyrings/docker.gpg':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    require => Exec['docker-gpg-key'],
  }

  file { '/etc/apt/sources.list.d/docker.list':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu jammy stable\n",
    require => File['/etc/apt/keyrings/docker.gpg'],
    notify  => Exec['apt-update-docker'],
  }

  exec { 'apt-update-docker':
    command     => '/usr/bin/apt-get update -y',
    refreshonly => true,
    timeout     => 300,
    tries       => 3,
    try_sleep   => 10,
  }

  package { ['docker-ce', 'docker-ce-cli', 'containerd.io', 'docker-compose-plugin']:
    ensure  => installed,
    require => [File['/etc/apt/sources.list.d/docker.list'], Exec['apt-update-docker']],
  }

  service { 'docker':
    ensure  => running,
    enable  => true,
    require => Package['docker-ce'],
  }

  # The application user needs to be able to talk to the Docker socket for
  # diagnostics; the container itself is managed by systemd as root.
  exec { "add-${app_user}-to-docker-group":
    command => "/usr/sbin/usermod -aG docker ${app_user}",
    unless  => "/usr/bin/id -nG ${app_user} | /bin/grep -qw docker",
    require => [Package['docker-ce'], User[$app_user]],
  }
}
