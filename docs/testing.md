# Testing

Backend: JUnit 5, Mockito, AssertJ, ArchUnit, MockMvc, workflow golden demo.

```bash
export JAVA_HOME=…/corretto-17
mvn clean verify
```

Frontend: Angular TestBed (Karma/Jasmine scaffold). Facade unit test included.

```bash
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless
```
