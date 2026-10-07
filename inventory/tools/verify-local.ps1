param([string]$Repository="", [string]$Jdk=$env:JAVA_HOME)
$ErrorActionPreference="Stop"
$projectDir=Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectDir
if(-not $Repository) {
 $Repository=Join-Path $projectDir ".build-cache/repository"
 if(-not(Test-Path -LiteralPath $Repository)) { $Repository=Join-Path $env:USERPROFILE ".m2/repository" }
}
if(-not $Jdk) { $Jdk="C:/Program Files/Java/jdk-24" }
$java=Join-Path $Jdk "bin/java.exe"
$javac=Join-Path $Jdk "bin/javac.exe"
$jars=@(Get-ChildItem -LiteralPath $Repository -Recurse -Filter *.jar)
if($jars.Count -eq 0) { throw "No hay dependencias locales; ejecuta Maven con acceso a Central." }
$runtimeCp=($jars | ForEach-Object FullName)-join ";"
New-Item -ItemType Directory -Force target/local-classes | Out-Null
$sources=@(Get-ChildItem src/main/java,src/test/java -Recurse -Filter *.java | ForEach-Object FullName)
$ErrorActionPreference="Continue"
& $java tools/LocalCompile.java $runtimeCp target/local-classes $sources tools/LocalTestRunner.java
$ErrorActionPreference="Stop"
if($LASTEXITCODE -ne 0) { throw "Fallo compilacion" }
Copy-Item src/main/resources/* target/local-classes -Recurse -Force
$ErrorActionPreference="Continue"
& $java -cp "target/local-classes;$runtimeCp" LocalTestRunner com.techstore.inventory.InventoryApplicationTests com.techstore.inventory.SecurityInventoryTests com.techstore.inventory.OAuthConfigurationTests
$ErrorActionPreference="Stop"
if($LASTEXITCODE -ne 0) { throw "Hay pruebas fallidas" }
Write-Output "Compilacion y pruebas locales correctas."
