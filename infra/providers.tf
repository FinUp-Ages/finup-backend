provider "aws" {
  region = var.aws_region
  # Vazio = cadeia padrao da AWS (AWS_PROFILE, variaveis de ambiente, SSO...).
  profile = var.aws_profile

  default_tags {
    tags = {
      project    = "finup"
      managed-by = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
