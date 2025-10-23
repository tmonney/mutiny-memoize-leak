# Mutiny memo leak reproducer

Mutiny 2.9.4 introduces a memory leak where the `awaiters` of a memoized Uni are not properly cleaned up.

To illustrate the problem:

```shell
# Tests fail
mvn test -Dmutiny.version=2.9.4
mvn test -Dmutiny.version=3.0.0
```

```shell
# Tests pass
mvn test -Dmutiny.version=2.9.2
mvn test -Dmutiny.version=2.9.3
```
