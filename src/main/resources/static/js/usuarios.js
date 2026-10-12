document.addEventListener('DOMContentLoaded', function () {
    const formulario = document.getElementById('formUsuario');
    if (!formulario || formulario.dataset.rolesBloqueados === 'true') {
        return;
    }
    const area = formulario.querySelector('select[name="area"]');
    const rol = formulario.querySelector('select[name="rol.idRol"]');
    const adicionales = Array.from(formulario.querySelectorAll('.rol-adicional'));
    const sinAdicionales = formulario.querySelector('.sin-roles-adicionales');

    function filtrarRoles() {
        Array.from(rol.options).forEach(function (opcion) {
            if (opcion.value) {
                const disponible = opcion.dataset.area === area.value;
                opcion.hidden = !disponible;
                opcion.disabled = !disponible;
            }
        });
        if (rol.selectedOptions[0] && rol.selectedOptions[0].disabled) {
            rol.value = '';
        }
        let visibles = 0;
        adicionales.forEach(function (contenedor) {
            const check = contenedor.querySelector('input');
            const disponible = contenedor.dataset.area === area.value && check.value !== rol.value;
            contenedor.hidden = !disponible;
            check.disabled = !disponible;
            if (!disponible) {
                check.checked = false;
            } else {
                visibles++;
            }
        });
        sinAdicionales.hidden = visibles > 0;
    }

    area.addEventListener('change', filtrarRoles);
    rol.addEventListener('change', filtrarRoles);
    filtrarRoles();
});
