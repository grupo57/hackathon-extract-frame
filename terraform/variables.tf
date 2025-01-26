variable "lambda_function_name" {
  default = "hackathon-extract-frame"
}

variable "s3_bucket_name" {
  default = "hackathon-extract-frame-bucket"
}

variable "layer_ffmpeg_s3_key" {
  default = "ffmpeg-layer.zip"
}

variable "api_gateway_name" {
  default = "hackathon-extract-frame-api-gateway"
}