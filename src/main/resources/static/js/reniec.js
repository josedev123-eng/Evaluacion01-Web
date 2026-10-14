// Al escribir los 8 dígitos del DNI, completa nombres y apellidos desde RENIEC.
// Si la consulta no está configurada o falla, el formulario se llena a mano como siempre.
document.addEventListener('DOMContentLoaded', function () {
    const campo = document.querySelector('input[data-reniec-url]');
    const nombres = document.getElementById('nombres');
    const apellidos = document.getElementById('apellidos');
    const estado = document.getElementById('dniEstado');
    if (!campo || !nombres || !apellidos || !estado) {
        return;
    }
    let ultimoConsultado = '';

    function mostrar(texto, clase) {
        estado.textContent = texto;
        estado.className = 'form-text ' + clase;
    }

    async function consultar() {
        const dni = campo.value.trim();
        if (!/^\d{8}$/.test(dni) || dni === ultimoConsultado) {
            return;
        }
        ultimoConsultado = dni;
        mostrar('Consultando RENIEC...', 'text-secondary');
        try {
            const respuesta = await fetch(campo.dataset.reniecUrl + dni, {
                headers: {'Accept': 'application/json'},
                credentials: 'same-origin'
            });
            if (respuesta.status === 503) {
                mostrar('', '');
                return;
            }
            const datos = await respuesta.json();
            if (respuesta.ok) {
                nombres.value = datos.nombres;
                apellidos.value = datos.apellidos;
                if (datos.simulado) {
                    mostrar('Datos de DEMOSTRACIÓN (simulados): no provienen de RENIEC. Corrígelos si hace falta.', 'text-warning');
                } else {
                    mostrar('Datos completados desde RENIEC. Revísalos antes de guardar.', 'text-success');
                }
            } else {
                mostrar(datos.mensaje || 'No se pudo consultar RENIEC.', 'text-warning');
            }
        } catch (error) {
            mostrar('No se pudo consultar RENIEC. Completa los datos a mano.', 'text-warning');
        }
    }

    campo.addEventListener('input', consultar);
    campo.addEventListener('change', consultar);
});
