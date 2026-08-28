# Ubuntu 22.04 LTS, resolved per-region from Canonical's official AMIs.
# Never a hardcoded AMI id: those are region-specific and go stale.
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

# The platform generates and stores the project keypair; terraform registers the
# public half. This is the ONLY injection path — authorized_keys is seeded at
# launch by cloud-init from this key pair.
resource "aws_key_pair" "main" {
  key_name   = "${var.project_name}-key"
  public_key = var.ssh_public_key

  tags = {
    Name = "${var.project_name}-key"
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = local.app_subnet_id
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = aws_key_pair.main.key_name
  iam_instance_profile   = aws_iam_instance_profile.app.name

  associate_public_ip_address = true

  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    # IMDSv2 only: blocks the SSRF-to-credential-theft path against IMDSv1.
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  # Minimal bootstrap only — real configuration is Puppet's job in the
  # configure stage. This just ensures python3 and curl exist so the agent
  # bootstrap script can run.
  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail
    export DEBIAN_FRONTEND=noninteractive
    for i in $(seq 1 30); do
      if apt-get update -y; then break; fi
      echo "apt-get update failed, retrying ($i/30)"
      sleep 10
    done
    apt-get install -y --no-install-recommends curl ca-certificates python3
  EOF

  # IAM propagation can lag instance creation; without this the first apply
  # intermittently fails to attach the profile and passes only on retry.
  depends_on = [aws_iam_instance_profile.app]

  tags = {
    Name = "${var.project_name}-app"
  }
}

# A stable address that survives instance replacement, so the verify stage and
# any DNS record do not chase an ephemeral public IP.
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name = "${var.project_name}-eip"
  }

  depends_on = [aws_instance.app]
}
