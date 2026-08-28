# Instance role: least privilege. The instance needs to ship logs and metrics to
# CloudWatch and nothing else. No wildcard resource on anything mutable.

data "aws_iam_policy_document" "assume_ec2" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.project_name}-app-role"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json

  tags = {
    Name = "${var.project_name}-app-role"
  }
}

data "aws_iam_policy_document" "app_logs" {
  statement {
    sid    = "WriteApplicationLogs"
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams",
    ]

    resources = [
      "${aws_cloudwatch_log_group.app.arn}:*",
      "${aws_cloudwatch_log_group.system.arn}:*",
    ]
  }

  statement {
    sid    = "PublishInstanceMetrics"
    effect = "Allow"

    actions = ["cloudwatch:PutMetricData"]

    # PutMetricData does not support resource-level permissions; it is scoped by
    # namespace instead.
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["${var.project_name}/application"]
    }
  }
}

resource "aws_iam_role_policy" "app_logs" {
  name   = "${var.project_name}-app-logs"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_logs.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project_name}-app-profile"
  role = aws_iam_role.app.name
}
