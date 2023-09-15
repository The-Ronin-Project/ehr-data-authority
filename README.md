# EHR DATA Authority
[![codecov](https://codecov.io/gh/projectronin/ehr-data-authority/branch/master/graph/badge.svg?token=6066BAwJYk)](https://app.codecov.io/gh/projectronin/ehr-data-authority/branch/master)
[![Tests](https://github.com/projectronin/ehr-data-authority/actions/workflows/test.yml/badge.svg)](https://github.com/projectronin/ehr-data-authority/actions/workflows/test.yml)
[![Integration Tests](https://github.com/projectronin/ehr-data-authority/actions/workflows/integration_test.yml/badge.svg)](https://github.com/projectronin/ehr-data-authority/actions/workflows/integration_test.yml)
[![Lint](https://github.com/projectronin/ehr-data-authority/actions/workflows/lint.yml/badge.svg)](https://github.com/projectronin/ehr-data-authority/actions/workflows/lint.yml)

### Local Test Setup Option
EHRDA has the option to use local storage instead of Aidbox to test.  It performs NO validation and produces NO Kafka events. It simply take in new resources, stores them in an internal storage and responds with success.

To use this feature set the following in the docker-compose:  
SPRING_PROFILES_ACTIVE: "local"
