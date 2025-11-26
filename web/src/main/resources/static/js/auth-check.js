// auth-check.js
document.body.style.display = 'none';

(async () => {
    try {
        const res = await fetch('http://localhost:8081/api/auth/me', {
            method: 'GET',
            credentials: 'include', // отправляет JSESSIONID
        });

        if (res.ok) {
            // авторизован — показываем страницу
            document.body.style.display = 'block';
        } else {
            // редирект на login
            window.location.href = '/login.html';
        }
    } catch (err) {
        console.error('Ошибка проверки сессии', err);
        window.location.href = '/login.html';
    }
})();
