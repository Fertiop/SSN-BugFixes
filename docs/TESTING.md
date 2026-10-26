# SSN-BugFixes — тестовая инфраструктура

## Среда

Minecraft 1.20.1 / Forge 47.3.0 / Java 17, релизный SSN 1.13.2 (Modrinth `cVIr8Vz1/UmSTgKWE`) и FLib 0.0.16 (`FAUSd09K/CDihtEoy`). Сторонние JAR скачиваются Gradle и не перепаковываются в наш JAR.

## Проверки

**1. Исходный бинарник SSN:** 6 тестов Python/JDK `javap` в `tests/static/test_ssn_1132_bytecode.py`. SHA-256 фиксирован, но эти тесты подтверждают лишь факты об оригинальном коде.

```sh
SSN_JAR=/path/to/SimpleStorageNetwork-1.20.1-1.13.2.jar python -m unittest discover -s tests/static -p 'test_*.py' -v
```

**2. GREEN с патчами:** стандартный GameTest-сервер Forge, 20 игровых проверок, включая настоящие TileRequest, ItemBuilder, ContainerNetwork, NetworkModule и тестовые IItemHandler с предсказуемыми исключениями.

```sh
./gradlew --no-daemon build
./gradlew --no-daemon runGameTestServer
```

**3. RED без Mixin:** та же игровая среда с оригинальным SSN без регистрации наших Mixin. Десять конкретных регрессионных сценариев **обязаны** обнаружить исходные ошибки.

```sh
./gradlew --no-daemon -PssnBaseline runGameTestServer
```

GameTest нормализует camelCase в нижний регистр; сообщения обязательных ошибок имеют `failed!`, необязательных — `failed.`. CI учитывает оба. В RED Gradle ожидаемо возвращает ненулевой exit code, поэтому скрипт проверяет **конкретные отказы**, а не считает любой неуспех нормальным.

**4. Упаковка:** GitHub Actions проверяет `MixinConfigs`, `ssnbugfixes.refmap.json` и отсутствие `gametest/**`/тестового `empty.nbt` в выпускном JAR.

## Проверенное выполнение

[CI 40387833982](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40387833982): все стадии прошли; 20 GREEN GameTests и 10 конкретных RED. После этого все четыре бывшие optional проверки переведены в `required=true` и GREEN-парсер ужесточён; эту последующую конфигурацию следует перепроверить в GitHub Actions перед релизом.

## Что не покрывает GameTest

Дисковое сохранение и повторный запуск сервера; actual client-only tooltip/GUI; весь набор внешних модов; пользовательские сценарии многочасовой работы, полномасштабная проверка NBT и других рецептов. См. [TEST_CASES.md](TEST_CASES.md).

## Запуск в GitHub

Основной workflow `.github/workflows/ssn-tests.yml` автоматически выполняется для PR. Ручной `.github/workflows/gametest-manual.yml` запускается через GitHub Actions → SSN 1.13.2 GameTests (manual) → Run workflow. Не считать выходной код 0 доказательством без записей об обнаружении 20 GameTests, списке исходных RED и отсутствии незапланированных отказов.
