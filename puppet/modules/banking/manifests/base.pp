# Base OS preparation: application user, directories, and required packages.
#
# Uses only built-in Puppet resource types — no Forge modules, because the
# masterless apply on the host has no module tree beyond what the configure
# stage copies over.
class banking::base (
  String $app_user  = lookup('banking::app_user'),
  String $app_group = lookup('banking::app_group'),
  String $app_dir   = lookup('banking::app_dir'),
  String $log_dir   = lookup('banking::log_dir'),
) {

  # Refresh the package index. Deliberately NO cache_valid_time: a freshly
  # provisioned cloud image ships a prebuilt cache that looks fresh but names
  # superseded package versions, and skipping the refresh produces 404s on
  # every subsequent install.
  exec { 'apt-update':
    command     => '/usr/bin/apt-get update -y',
    timeout     => 300,
    tries       => 5,
    try_sleep   => 10,
    logoutput   => on_failure,
    refreshonly => false,
  }

  package { ['ca-certificates', 'curl', 'gnupg', 'jq', 'unzip']:
    ensure  => installed,
    require => Exec['apt-update'],
  }

  group { $app_group:
    ensure => present,
    system => true,
  }

  user { $app_user:
    ensure     => present,
    gid        => $app_group,
    system     => true,
    home       => $app_dir,
    managehome => false,
    shell      => '/usr/sbin/nologin',
    require    => Group[$app_group],
  }

  file { $app_dir:
    ensure  => directory,
    owner   => $app_user,
    group   => $app_group,
    mode    => '0750',
    require => User[$app_user],
  }

  file { $log_dir:
    ensure  => directory,
    owner   => $app_user,
    group   => $app_group,
    mode    => '0750',
    require => User[$app_user],
  }

  # Set the system timezone to UTC so log timestamps are unambiguous.
  file { '/etc/timezone':
    ensure  => file,
    content => "Etc/UTC\n",
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    notify  => Exec['reconfigure-timezone'],
  }

  exec { 'reconfigure-timezone':
    command     => '/usr/sbin/dpkg-reconfigure -f noninteractive tzdata',
    refreshonly => true,
  }
}
