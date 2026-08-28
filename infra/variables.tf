variable "project_name" {
  description = "Branch-scoped project name used as the prefix for every resource."
  type        = string
}

variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "EC2 instance type for the application server."
  type        = string
  default     = "t3.small"
}

variable "ssh_public_key" {
  description = "Public half of the platform-managed project SSH keypair."
  type        = string
}

variable "db_username" {
  description = "Master username for the PostgreSQL instance."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Master password for the PostgreSQL instance."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage for the database, in GiB."
  type        = number
  default     = 20
}

variable "db_backup_retention_days" {
  description = "Number of days of automated RDS backups to retain."
  type        = number
  default     = 7
}

variable "app_port" {
  description = "Port the Spring Boot application listens on behind nginx."
  type        = number
  default     = 8080
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for application and system logs."
  type        = number
  default     = 30
}

variable "cpu_alarm_threshold" {
  description = "Average CPU percentage that triggers the CloudWatch alarm."
  type        = number
  default     = 80
}
