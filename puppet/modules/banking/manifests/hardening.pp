# CIS-aligned host hardening.
#
# Applied LAST in site.pp on purpose: SSH policy changes must not lock out the
# configure stage that is still running. Nothing here moves the SSH port or
# disables the key-based login the pipeline depends on.
class banking::hardening {

  # Key-only SSH, no root login, no empty passwords.
  file { '/etc/ssh/sshd_config.d/99-hardening.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0600',
    content => @(SSHD),
      # Managed by Puppet — CIS-aligned SSH policy.
      PasswordAuthentication no
      PermitRootLogin no
      PermitEmptyPasswords no
      ChallengeResponseAuthentication no
      X11Forwarding no
      MaxAuthTries 4
      LoginGraceTime 30
      ClientAliveInterval 300
      ClientAliveCountMax 2
      | SSHD
    notify  => Exec['sshd-config-test'],
  }

  # Validate the config before reloading. A syntax error that reaches a reload
  # can end remote access to the host entirely.
  exec { 'sshd-config-test':
    command     => '/usr/sbin/sshd -t',
    refreshonly => true,
    logoutput   => on_failure,
    notify      => Service['ssh'],
  }

  service { 'ssh':
    ensure => running,
    enable => true,
  }

  # Unattended security updates.
  package { ['unattended-upgrades', 'apt-listchanges']:
    ensure => installed,
  }

  file { '/etc/apt/apt.conf.d/20auto-upgrades':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @(AUTOUP),
      # Managed by Puppet.
      APT::Periodic::Update-Package-Lists "1";
      APT::Periodic::Unattended-Upgrade "1";
      APT::Periodic::AutocleanInterval "7";
      | AUTOUP
    require => Package['unattended-upgrades'],
  }

  # Kernel network hardening.
  file { '/etc/sysctl.d/99-banking-hardening.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => @(SYSCTL),
      # Managed by Puppet — network stack hardening.
      net.ipv4.conf.all.accept_redirects = 0
      net.ipv4.conf.default.accept_redirects = 0
      net.ipv4.conf.all.send_redirects = 0
      net.ipv4.conf.default.send_redirects = 0
      net.ipv4.conf.all.accept_source_route = 0
      net.ipv4.conf.default.accept_source_route = 0
      net.ipv4.conf.all.log_martians = 1
      net.ipv4.tcp_syncookies = 1
      kernel.randomize_va_space = 2
      | SYSCTL
    notify  => Exec['apply-sysctl'],
  }

  exec { 'apply-sysctl':
    command     => '/usr/sbin/sysctl --system',
    refreshonly => true,
    logoutput   => on_failure,
  }
}
