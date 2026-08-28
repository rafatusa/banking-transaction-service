resource "aws_db_subnet_group" "main" {
  name = "${var.project_name}-db-subnets"
  # RDS requires subnets spanning at least two availability zones.
  subnet_ids = local.sorted_subnet_ids

  tags = {
    Name = "${var.project_name}-db-subnets"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "16.4"
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_allocated_storage * 2
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "banking"
  username = var.db_username
  password = var.db_password
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  # Never reachable from the internet: the app security group is the only path in.
  publicly_accessible = false
  multi_az            = false

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:30-sun:05:30"
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade = true
  deletion_protection        = false

  # Demo/Tier-2 posture: teardown must be able to remove the database cleanly.
  # Set skip_final_snapshot = false and provide a final_snapshot_identifier
  # before using this for anything with real customer data.
  skip_final_snapshot = true

  performance_insights_enabled = false
  apply_immediately            = true

  tags = {
    Name = "${var.project_name}-db"
  }
}
