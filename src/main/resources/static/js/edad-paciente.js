// Muestra la edad del paciente al elegir su fecha de nacimiento, sin recargar la página.
document.addEventListener('DOMContentLoaded', function () {
    var fecha = document.getElementById('fechaNacimiento');
    var aviso = document.getElementById('edadCalculada');
    if (!fecha || !aviso) {
        return;
    }

    function calcularEdad() {
        if (!fecha.value) {
            aviso.textContent = '';
            return;
        }
        var nacimiento = new Date(fecha.value);
        var hoy = new Date();
        var edad = hoy.getFullYear() - nacimiento.getFullYear();
        // Resta un año si todavía no ha cumplido años en lo que va del año.
        var mes = hoy.getMonth() - nacimiento.getMonth();
        if (mes < 0 || (mes === 0 && hoy.getDate() < nacimiento.getDate())) {
            edad = edad - 1;
        }
        if (edad < 0 || isNaN(edad)) {
            aviso.textContent = '';
            return;
        }
        aviso.textContent = 'Edad: ' + edad + ' años';
    }

    fecha.addEventListener('change', calcularEdad);
    calcularEdad();
});
