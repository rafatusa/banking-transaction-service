# Deploys the application container and manages it under systemd.
#
# systemd (not `docker run -d`) owns the container lifecycle so the service
# survives a reboot and restarts on failure — the boot-safe process management
# requirement.
class banking::app (
  String  $app_user       = lookup('banking::app_user'),
  String  $app_group      = lookup('banking::app_group'),
  String  $app_dir        = lookup('banking::app_dir'),
  String  $container_name = lookup('banking::container_name'),
  Integer $app_port       = lookup('banking::app_port'),
  String  $image          = lookup('banking::image'),
  String  $ghcr_user      = lookup('banking::ghcr_user'),
  String  $ghcr_token     = lookup('banking::ghcr_token'),
  String  $db_host        = lookup('banking::db_host'),
  Integer $db_port        = lookup('banking::db_port'),
  String  $db_name        = lookup('banking::db_name'),
  String  $db_username    = lookup('banking::db_username'),
  String  $db_password    = lookup('banking::db_password'),
  String  $jwt_secret     = lookup('banking::jwt_secret'),
  Integer $jwt_validity   = lookup('banking::jwt_validity_minutes'),
  String  $seed_username  = lookup('banking::seed_username'),
  String  $seed_password  = lookup('banking::seed_password'),
) {

  # Runtime configuration. Mode 0640 and owned by the app user: the database
  # password and JWT signing key live here and must not be world-readable.
  file { "${app_dir}/app.env":
    ensure    => file,
    owner     => $app_user,
    group     => $app_group,
    mode      => '0640',
    show_diff => false,
    content   => epp('banking/app.env.epp', {
      'db_host'       => $db_host,
      'db_port'       => $db_port,
      'db_name'       => $db_name,
      'db_username'   => $db_username,
      'db_password'   => $db_password,
      'jwt_secret'    => $jwt_secret,
      'jwt_validity'  => $jwt_validity,
      'app_port'      => $app_port,
      'seed_username' => $seed_username,
      'seed_password' => $seed_password,
    }),
    notify    => Service['banking-app'],
  }

  # Authenticate to GHCR so the image can be pulled. Credentials go via an
  # environment variable and stdin, never on the command line where they would
  # appear in the process table.
  exec { 'ghcr-login':
    command     => '/bin/bash -c "echo \"$GHCR_TOKEN\" | docker login ghcr.io -u \"$GHCR_USER\" --password-stdin"',
    environment => ["GHCR_TOKEN=${ghcr_token}", "GHCR_USER=${ghcr_user}"],
    logoutput   => on_failure,
    timeout     => 120,
    require     => Service['docker'],
  }

  exec { 'pull-app-image':
    command   => "/usr/bin/docker pull ${image}",
    unless    => "/usr/bin/docker image inspect ${image}",
    logoutput => on_failure,
    timeout   => 600,
    tries     => 3,
    try_sleep => 15,
    require   => Exec['ghcr-login'],
    notify    => Service['banking-app'],
  }

  file { '/etc/systemd/system/banking-app.service':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('banking/banking-app.service.epp', {
      'app_dir'        => $app_dir,
      'container_name' => $container_name,
      'image'          => $image,
      'app_port'       => $app_port,
    }),
    notify  => [Exec['systemd-daemon-reload'], Service['banking-app']],
  }

  exec { 'systemd-daemon-reload':
    command     => '/usr/bin/systemctl daemon-reload',
    refreshonly => true,
  }

  service { 'banking-app':
    ensure    => running,
    enable    => true,
    require   => [
      File['/etc/systemd/system/banking-app.service'],
      File["${app_dir}/app.env"],
      Exec['pull-app-image'],
      Exec['systemd-daemon-reload'],
    ],
  }

  # Block until the application answers its own health endpoint, so a failed
  # startup fails the configure stage rather than surfacing later in verify.
  exec { 'wait-for-app-health':
    command   => "/bin/bash -c 'for i in \$(seq 1 60); do curl -fsS http://127.0.0.1:${app_port}/actuator/health | grep -q UP && exit 0; sleep 5; done; echo \"application did not become healthy\"; docker logs --tail 100 ${container_name}; exit 1'",
    logoutput => true,
    timeout   => 400,
    require   => Service['banking-app'],
  }
}
