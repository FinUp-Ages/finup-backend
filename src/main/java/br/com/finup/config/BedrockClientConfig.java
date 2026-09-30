package br.com.finup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/** Cliente do Bedrock Runtime: chaves explicitas quando configuradas, senao a cadeia padrao. */
@Configuration
@EnableConfigurationProperties(BedrockProperties.class)
public class BedrockClientConfig {

  @Bean
  public BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
    var builder = BedrockRuntimeClient.builder().region(Region.of(properties.region()));

    if (StringUtils.hasText(properties.accessKeyId())
        && StringUtils.hasText(properties.secretAccessKey())) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }
    return builder.build();
  }
}
