output "app_public_ip" {
  description = "Stable public address of the application (Elastic IP)."
  value       = aws_eip.app.public_ip
}

output "app_instance_id" {
  description = "EC2 instance id of the application server."
  value       = aws_instance.app.id
}

output "db_address" {
  description = "Hostname of the PostgreSQL instance (private, reachable only from the app SG)."
  value       = aws_db_instance.main.address
}

output "db_port" {
  description = "Port of the PostgreSQL instance."
  value       = aws_db_instance.main.port
}

output "db_name" {
  description = "Name of the application database."
  value       = aws_db_instance.main.db_name
}

output "app_log_group" {
  description = "CloudWatch log group receiving application logs."
  value       = aws_cloudwatch_log_group.app.name
}

output "system_log_group" {
  description = "CloudWatch log group receiving system logs."
  value       = aws_cloudwatch_log_group.system.name
}

output "app_url" {
  description = "Base URL of the deployed service."
  value       = "http://${aws_eip.app.public_ip}"
}
