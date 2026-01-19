document.addEventListener('DOMContentLoaded', function() {
    const forms = document.querySelectorAll('form');

    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            e.preventDefault();

            // Simular envío exitoso (aquí conectarías con tu Java backend)
            const formData = new FormData(form);
            console.log('Datos enviados:', Object.fromEntries(formData));

            // Redirigir al dashboard tras login exitoso
            if (form.id === 'loginForm') {
                setTimeout(() => {
                    window.location.href = 'dashboard.html';
                }, 1000);
            }
        });
    });
});

