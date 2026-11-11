terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # State local por enquanto. Para o time, migrar para um backend S3 com lock:
  # backend "s3" { bucket = "...", key = "finup/infra.tfstate", region = "us-east-2", use_lockfile = true }
}
