document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.form-estado[data-confirmar]').forEach(function (formulario) {
        formulario.addEventListener('submit', function (evento) {
            if (!window.confirm(formulario.dataset.confirmar)) {
                evento.preventDefault();
            }
        });
    });
});
