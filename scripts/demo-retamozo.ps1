param(
    [ValidateRange(1024, 65535)]
    [int]$Puerto = 8095
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$classpathArchivo = Join-Path $raiz 'target\demo-classpath.txt'
Push-Location $raiz
try {
    & (Join-Path $raiz 'mvnw.cmd') -B test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathArchivo" '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el classpath de demostracion.' }
    $classpath = (Join-Path $raiz 'target\test-classes') + ';' + (Join-Path $raiz 'target\classes') + ';' + (Get-Content -Raw $classpathArchivo).Trim()
    # Fijar todas las opciones de aislamiento: nunca usar la conexion MySQL de desarrollo.
    & java -cp $classpath com.evaluacion01.tecsup.TecsupApplication `
        '--spring.profiles.active=test,demo' "--server.port=$Puerto" '--server.address=127.0.0.1' `
        '--spring.datasource.url=jdbc:h2:mem:demo-retamozo;MODE=MySQL;DB_CLOSE_DELAY=-1' `
        '--spring.datasource.driver-class-name=org.h2.Driver' '--spring.datasource.username=sa' '--spring.datasource.password=' `
        '--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect' '--spring.jpa.hibernate.ddl-auto=create-drop' `
        '--spring.sql.init.mode=never' '--spring.jpa.show-sql=false' `
        '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration'
    if ($LASTEXITCODE -ne 0) { throw 'La demostracion finalizo con un error.' }
} finally {
    Pop-Location
}
