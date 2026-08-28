terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    # Reads GitHub's published Actions runner CIDR ranges (api.github.com/meta)
    # so the SSH security-group rule can be scoped to the CI runners instead of
    # being left open to 0.0.0.0/0. See infra/network.tf.
    http = {
      source  = "hashicorp/http"
      version = "~> 3.4"
    }
  }

  # Backend configuration is supplied entirely by -backend-config flags at init
  # time (bucket / key / region come from platform secrets). Backend blocks cannot
  # reference variables, and hardcoding a key would share state across branches.
  backend "s3" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "udap"
    }
  }
}
