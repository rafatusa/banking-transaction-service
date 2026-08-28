# 1. Puppet only for configuration management

Date: 2026-08-28

## Status

Accepted

## Context

The original requirement listed both Puppet and Ansible: "Puppet for server bootstrap, Ansible for
application deployment."

The deployment target is a single EC2 instance. Two configuration management systems on one host
means two tools that both believe they own packages, users, file contents and service state. They
disagree eventually, and when they do the failure is confusing: a resource reverts between runs
with no single log explaining why.

There is also a platform consideration. Ansible is this platform's default mechanism for the
`configure` stage of VM targets and wires up natively; Puppet does not. Choosing Puppet means
hand-building the configure stage.

The user was asked to confirm and chose Puppet only.

## Decision

Puppet owns both server bootstrap and application deployment. Ansible is not used.

Puppet runs **masterless**: the configure stage copies the manifest tree to the host over SSH and
runs `puppet apply`. There is no Puppet Server.

The configure stage follows the platform's standard configure sequence — read terraform outputs,
write the SSH key from a secret, `ssh-keyscan`, wait for sshd, render node data, transfer, apply —
substituting `puppet apply` for `ansible-playbook`.

## Consequences

**Positive**

- One source of truth for host state. No drift between competing tools.
- Puppet's resource model is declarative and idempotent, which the recovery path depends on:
  a failed deploy re-runs the entire configure stage, and that must be safe.
- No Puppet Server to provision, secure, certify or pay for. For one node a master would be
  more infrastructure than the thing it configures.

**Negative**

- The configure stage is hand-built rather than inherited from the platform default, so it
  carries more of our own code and more of our own maintenance.
- Masterless Puppet has no central reporting, no PuppetDB, no exported resources. For a single
  node none of these matter; if this grows to a fleet, revisit.
- Only built-in Puppet resource types are available. The host has no Forge modules installed,
  so `docker`, `nginx` and similar community modules are unavailable and the equivalent logic is
  written with `package`, `file`, `service` and guarded `exec` resources.

**Neutral**

- `puppet apply --detailed-exitcodes` returns 2 for "changes applied successfully". The
  configure stage treats exit codes 0 and 2 as success; anything else fails the deploy.
