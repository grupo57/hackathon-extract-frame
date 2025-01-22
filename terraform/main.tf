# S3 Bucket para código e layers
resource "aws_s3_bucket" "deploy_bucket" {
  bucket = var.s3_bucket_name
  acl    = "private"
}

# Lambda Layer para FFmpeg
resource "aws_lambda_layer_version" "ffmpeg_layer" {
  s3_bucket = aws_s3_bucket.deploy_bucket.id
  s3_key    = var.layer_ffmpeg_s3_key
  compatible_runtimes = ["java11", "java17"]
}

# Função Lambda
resource "aws_lambda_function" "java_api_lambda" {
  function_name = var.lambda_function_name
  runtime       = "java17"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "com.example.Handler::handleRequest"

  s3_bucket        = aws_s3_bucket.deploy_bucket.id
  s3_key           = "lambda-function.jar"
  source_code_hash = filebase64sha256("target/lambda-function.jar")

  layers = [aws_lambda_layer_version.ffmpeg_layer.arn]

  environment {
    variables = {
      EXAMPLE_ENV = "example_value"
    }
  }
}

# IAM Role para Lambda
resource "aws_iam_role" "lambda_exec" {
  name = "lambda_exec_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Política de permissões para a Lambda
resource "aws_iam_role_policy" "lambda_policy" {
  name = "lambda_exec_policy"
  role = aws_iam_role.lambda_exec.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}
