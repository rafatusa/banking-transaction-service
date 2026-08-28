# Masterless Puppet entry point for the banking transaction service host.
#
# Applied by the configure stage with:
#   puppet apply manifests/site.pp --modulepath=modules --hiera_config=hiera.yaml
#
# Every class here is idempotent: a second apply reports no changes. The deploy
# pipeline relies on that, because recovery reruns the whole configure stage.

node default {

  # Ordering: harden and prepare the OS, install the container runtime, bring up
  # nginx, then start the application, then wire logging. The application class
  # depends on Docker being present; nginx is configured before the app so the
  # proxy is ready the moment the container is healthy.
  class { 'banking::base': }
  -> class { 'banking::docker': }
  -> class { 'banking::nginx': }
  -> class { 'banking::app': }
  -> class { 'banking::logging': }
  -> class { 'banking::hardening': }
}
