# Reuse the account's default VPC. The probe confirmed one exists
# (vpc-08750793f051e477b, 172.31.0.0/16, 6 subnets) and a Tier-2 single-instance
# deployment does not justify building and paying for a bespoke VPC + NAT.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# RDS requires subnets in at least two availability zones.
data "aws_subnet" "selected" {
  for_each = toset(data.aws_subnets.default.ids)
  id       = each.value
}

# GitHub publishes the CIDR ranges its hosted runners use. The configure stage
# SSHes from one of those runners, whose address is not knowable in advance, so
# the alternative to this data source is leaving port 22 open to the whole
# internet. Fetching the list lets the SSH rule be scoped to GitHub's actions
# ranges instead — the runner still connects, everyone else is refused at the
# security group.
data "http" "github_meta" {
  url = "https://api.github.com/meta"

  request_headers = {
    Accept = "application/vnd.github+json"
  }
}

locals {
  # Deterministic ordering so the chosen subnet does not churn between applies.
  sorted_subnet_ids = sort(data.aws_subnets.default.ids)
  app_subnet_id     = local.sorted_subnet_ids[0]

  # IPv4 ranges only: the security group rules below are cidr_ipv4. The API
  # returns both families in one list.
  github_actions_ipv4 = [
    for cidr in jsondecode(data.http.github_meta.response_body).actions :
    cidr if !strcontains(cidr, ":")
  ]
}

# ---- Application security group ---------------------------------------------
resource "aws_security_group" "app" {
  name        = "${var.project_name}-app-sg"
  description = "Application server: public HTTP/HTTPS and administrative SSH"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-app-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# HTTP and HTTPS are open to the internet BY DESIGN: this is a public web
# application and nginx terminates traffic on 80/443. Trivy flags world-open
# ingress generically; for these two ports it is the requirement, not a
# misconfiguration, and the accepted risk is recorded in .trivyignore.
resource "aws_vpc_security_group_ingress_rule" "app_http" {
  security_group_id = aws_security_group.app.id
  description       = "Public HTTP served by nginx"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "app_https" {
  security_group_id = aws_security_group.app.id
  description       = "Public HTTPS (reserved for when a domain and certificate exist)"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# SSH is NOT open to the internet. One rule per GitHub Actions CIDR, so only the
# CI runners that execute the configure stage can reach port 22.
#
# for_each (not count) keyed by the CIDR string itself: GitHub reorders and
# edits this list over time, and a count-indexed set would destroy and recreate
# unrelated rules whenever an entry shifted position.
#
# Operator note: this means an engineer cannot SSH in from a laptop. That is
# deliberate — use AWS SSM Session Manager (the instance profile already allows
# it) rather than widening this rule.
resource "aws_vpc_security_group_ingress_rule" "app_ssh" {
  for_each = toset(local.github_actions_ipv4)

  security_group_id = aws_security_group.app.id
  description       = "SSH for the Puppet configure stage (GitHub Actions runner)"
  cidr_ipv4         = each.value
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "app_egress" {
  security_group_id = aws_security_group.app.id
  description       = "Outbound: package mirrors, GHCR, CloudWatch"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ---- Database security group ------------------------------------------------
resource "aws_security_group" "db" {
  name        = "${var.project_name}-db-sg"
  description = "PostgreSQL: reachable only from the application security group"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-db-sg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# The database is never exposed to the internet: ingress references the app
# security group, not a CIDR block.
resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL from the application instance only"
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
