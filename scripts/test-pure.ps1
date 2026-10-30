$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $gradleLib = (Get-ChildItem .tools/gradle/wrapper/dists -Directory -Recurse -Filter gradle-8.13 | Select-Object -First 1).FullName + '/lib'
    $testCp = ((Get-ChildItem $gradleLib -Recurse -File | Where-Object { $_.Name -match '^(kotlin-stdlib|junit-4|hamcrest)' }).FullName -join ';')
    $dependencyJars = (Get-ChildItem .tools/gradle/caches/modules-2/files-2.1 -Recurse -File -Filter *.jar | Where-Object { $_.Name -match '^(commons-compress|commons-io|commons-lang3|commons-codec)' }).FullName -join ';'
    $testCp += ';' + $dependencyJars
    $sourceRoot = 'app/src/main/kotlin/com/edib/openwhispr'
    $testRoot = 'app/src/test/kotlin/com/edib/openwhispr'
    $names = @('ProviderConfig', 'InsightCalculator', 'RetentionPolicy', 'DictationSession', 'SafeModelArchive')
    $sources = @("$sourceRoot/DictationLanguage.kt") + @($names | ForEach-Object { "$sourceRoot/$_.kt"; "$testRoot/${_}Test.kt" })
    New-Item -ItemType Directory -Force .tools/pure-tests | Out-Null
    & java '-Dfile.encoding=UTF-8' -cp "$gradleLib/*;$gradleLib/plugins/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $testCp -d .tools/pure-tests @sources
    if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed' }
    $tests = @($names | ForEach-Object { "com.edib.openwhispr.${_}Test" })
    & java '-Dfile.encoding=UTF-8' -cp ".tools/pure-tests;$testCp" org.junit.runner.JUnitCore @tests
    if ($LASTEXITCODE -ne 0) { throw 'JUnit failed' }
} finally { Pop-Location }


