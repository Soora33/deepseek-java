FROM openjdk:17-oracle
WORKDIR /usr/local
ADD ./target/llm.jar .
CMD ["java","-jar","llm.jar","--spring.config.location=/usr/local/application.yml"]
