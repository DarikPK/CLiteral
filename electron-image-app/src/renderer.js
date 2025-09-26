// --- Elementos de la UI ---
const webview = document.getElementById('login-webview');
const extractBtn = document.getElementById('extract-images');
const gallery = document.getElementById('gallery');
const brightnessSlider = document.getElementById('brightness');
const contrastSlider = document.getElementById('contrast');
const generatePdfBtn = document.getElementById('generate-pdf');
const statusBar = document.getElementById('status-bar');

// --- Variables de Estado ---
let totalImagesToDownload = 0;
let downloadedImageCount = 0;

// --- Funciones Auxiliares ---
function updateStatus(message) {
    statusBar.textContent = message;
}

// Mejora el rendimiento de los sliders al evitar ejecuciones en cada píxel de movimiento.
function debounce(func, delay = 250) {
    let timeoutId;
    return (...args) => {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => {
            func.apply(this, args);
        }, delay);
    };
}

// --- Lógica de Generación de PDF ---
generatePdfBtn.addEventListener('click', () => {
    const images = gallery.querySelectorAll('img');
    if (images.length === 0) {
        alert('No hay imágenes para generar el PDF.');
        return;
    }
    updateStatus('Generando PDF...');
    const imagePaths = Array.from(images).map(img => img.src);
    window.api.send('generate-pdf', imagePaths);
});

window.api.receive('pdf-generated', ({ success, path }) => {
    if (success) {
        updateStatus(`PDF generado con éxito!`);
        alert(`¡PDF generado con éxito!\nGuardado en: ${path}`);
    } else {
        updateStatus('La generación del PDF fue cancelada o falló.');
        alert('La generación del PDF fue cancelada o falló.');
    }
});

// --- Lógica de Modificación de Imágenes ---
const applyImageModifications = debounce(() => {
    updateStatus('Aplicando filtros...');
    const brightness = brightnessSlider.value;
    const contrast = contrastSlider.value;
    const images = gallery.querySelectorAll('img');
    images.forEach((img) => {
        const imagePath = img.dataset.originalPath;
        const index = img.dataset.index;
        if (imagePath) {
            window.api.send('modify-image', { imagePath, brightness, contrast, index });
        }
    });
    setTimeout(() => updateStatus('Listo.'), 1000);
});

brightnessSlider.addEventListener('input', applyImageModifications);
contrastSlider.addEventListener('input', applyImageModifications);

window.api.receive('image-modified', ({ path, index }) => {
    const imgElement = gallery.querySelector(`img[data-index='${index}']`);
    if (imgElement) {
        imgElement.src = `${path}?t=${new Date().getTime()}`;
    }
});

// --- Lógica de Extracción de Imágenes ---
extractBtn.addEventListener('click', () => {
    updateStatus('Extrayendo URLs de las imágenes...');
    const extractionScript = `
        const pageUrl = window.location.href;
        const urls = Array.from(document.querySelectorAll('img')).map(img => {
            let src = img.getAttribute('src') || img.getAttribute('data-src');
            if (!src) return null;
            try { return new URL(src, pageUrl).href; } catch (e) { return null; }
        }).filter(Boolean);
        urls;
    `;

    webview.executeJavaScript(extractionScript).then((imageUrls) => {
        if (imageUrls && imageUrls.length > 0) {
            totalImagesToDownload = imageUrls.length;
            downloadedImageCount = 0;
            updateStatus(`Se encontraron ${totalImagesToDownload} imágenes. Descargando...`);
            gallery.innerHTML = '';
            imageUrls.forEach((url, index) => window.api.send('download-image', { url, index }));
        } else {
            updateStatus('No se encontraron imágenes en la página actual.');
            alert('No se encontraron imágenes para extraer en la página actual.');
        }
    });
});

window.api.receive('image-downloaded', ({ path, index }) => {
    downloadedImageCount++;
    updateStatus(`Descargando... ${downloadedImageCount} de ${totalImagesToDownload}`);
    const imgElement = document.createElement('img');
    imgElement.src = path;
    imgElement.dataset.originalPath = path.replace('file://', '');
    imgElement.dataset.index = index;
    gallery.appendChild(imgElement);

    if (downloadedImageCount === totalImagesToDownload) {
        updateStatus(`Descarga completa. ${totalImagesToDownload} imágenes en la galería.`);
    }
});

// --- Lógica del WebView ---
webview.addEventListener('did-navigate', (event) => {
    updateStatus('Listo.');
    // Habilita el botón de extracción solo en la página de visualización de partidas.
    if (event.url.includes('/servicio/busqueda/visualizar-partida')) {
        extractBtn.disabled = false;
    } else {
        extractBtn.disabled = true;
    }
});

// Opcional: abre las herramientas de desarrollo del WebView para depuración.
webview.addEventListener('dom-ready', () => {
    // webview.openDevTools();
});