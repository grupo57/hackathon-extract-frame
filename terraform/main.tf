# Lambda Layer para FFmpeg
resource "aws_lambda_layer_version" "ffmpeg_layer" {
  layer_name          = "ffmpeg-layer"
  s3_bucket           = var.s3_bucket_name
  s3_key              = var.layer_ffmpeg_s3_key
  compatible_runtimes = ["java11", "java17"]
}

# Função Lambda
resource "aws_lambda_function" "java_api_lambda" {
  function_name = var.lambda_function_name
  runtime       = "java17"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "com.example.Handler::handleRequest"

  s3_bucket        = var.s3_bucket_name
  s3_key           = "lambda-function.jar"
  source_code_hash = filebase64sha256("../target/lambda-function.jar")

  layers = [aws_lambda_layer_version.ffmpeg_layer.arn]

  environment {
    variables = {
      EXAMPLE_ENV = "example_value"
    }
  }
}

resource "random_id" "role_suffix" {
  byte_length = 4  # Tamanho do sufixo aleatório (4 bytes)
}

# IAM Role para Lambda
resource "aws_iam_role" "lambda_exec" {
  name = "lambda_exec_role_${random_id.role_suffix.hex}"

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
        Action   = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}


# API Gateway
# resource "aws_apigatewayv2_api" "http_api" {
#   name          = var.api_gateway_name
#   protocol_type = "HTTP"
# }

# # Integração Lambda com API Gateway
# resource "aws_apigatewayv2_integration" "lambda_integration" {
#   api_id             = aws_apigatewayv2_api.http_api.id
#   integration_type   = "AWS_PROXY"
#   integration_uri    = aws_lambda_function.java_api_lambda.invoke_arn
#   payload_format_version = "2.0"
# }

# # Rota para a API
# resource "aws_apigatewayv2_route" "default_route" {
#   api_id    = aws_apigatewayv2_api.http_api.id
#   route_key = "ANY /{proxy+}"

#   target = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
# }

# # Permissão para o API Gateway invocar a Lambda
# resource "aws_lambda_permission" "apigateway_permission" {
#   statement_id  = "AllowAPIGatewayInvoke"
#   action        = "lambda:InvokeFunction"
#   function_name = aws_lambda_function.java_api_lambda.function_name
#   principal     = "apigateway.amazonaws.com"

#   source_arn = "${aws_apigatewayv2_api.http_api.execution_arn}/*"
# }

# # Output da URL da API
# output "api_url" {
#   value = aws_apigatewayv2_api.http_api.api_endpoint
# }

