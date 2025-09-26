const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const Jimp = require('jimp');
const PDFDocument = require('pdfkit');

// --- Directorios de la Aplicación ---
// Se crea un directorio para las imágenes descargadas en la carpeta de datos del usuario.
const downloadsDir = path.join(app.getPath('userData'), 'image_downloads');
if (!fs.existsSync(downloadsDir)) {
  fs.mkdirSync(downloadsDir);
}

// --- Lógica de Comunicación Inter-Procesos (IPC) ---

// Escucha las peticiones de descarga de imágenes desde el proceso de renderizado.
ipcMain.on('download-image', (event, { url, index }) => {
  if (!url.startsWith('https://')) {
    console.error('URL no soportada. Solo se admiten HTTPS para la descarga.');
    return;
  }

  const filePath = path.join(downloadsDir, `image_${Date.now()}_${index}.jpg`);
  const file = fs.createWriteStream(filePath);

  https.get(url, (response) => {
    response.pipe(file);
    file.on('finish', () => {
      file.close(() => {
        event.sender.send('image-downloaded', { path: `file://${filePath}`, index });
      });
    });
  }).on('error', (err) => {
    fs.unlink(filePath, () => {}); // Elimina el archivo si hay un error.
    console.error('Error descargando la imagen:', err.message);
  });
});

// Escucha las peticiones de modificación de imágenes (brillo y contraste).
ipcMain.on('modify-image', (event, { imagePath, brightness, contrast, index }) => {
  const brightnessValue = (brightness - 100) / 100; // Rango -1 a 1
  const contrastValue = (contrast - 100) / 100;   // Rango -1 a 1

  Jimp.read(imagePath.replace('file://', ''))
    .then(image => {
      const modifiedPath = imagePath.replace('.jpg', '_modified.jpg');
      image.brightness(brightnessValue).contrast(contrastValue);
      image.write(modifiedPath.replace('file://', ''), () => {
        event.sender.send('image-modified', { path: `file://${modifiedPath}`, index });
      });
    })
    .catch(err => {
      console.error('Error modificando la imagen:', err);
    });
});

// Escucha la petición para generar el PDF.
ipcMain.on('generate-pdf', async (event, imagePaths) => {
  const { canceled, filePath } = await dialog.showSaveDialog({
    title: 'Guardar PDF',
    defaultPath: `extraccion_sunarp_${Date.now()}.pdf`,
    filters: [{ name: 'Documentos PDF', extensions: ['pdf'] }]
  });

  if (!canceled && filePath) {
    const doc = new PDFDocument({ autoFirstPage: false, layout: 'portrait' });
    doc.pipe(fs.createWriteStream(filePath));

    for (const imagePath of imagePaths) {
      try {
        const cleanedPath = imagePath.replace('file://', '').split('?')[0]; // Limpia la ruta
        const image = doc.openImage(cleanedPath);
        doc.addPage({ size: [image.width, image.height] });
        doc.image(image, 0, 0, { width: image.width, height: image.height });
      } catch (error) {
        console.error(`Error al añadir la imagen ${imagePath} al PDF:`, error);
      }
    }

    doc.end();
    event.sender.send('pdf-generated', { success: true, path: filePath });
  } else {
    event.sender.send('pdf-generated', { success: false });
  }
});

// --- Creación y Gestión de la Ventana Principal ---
function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      webviewTag: true
    }
  });

  mainWindow.loadFile('src/index.html');
}

app.whenReady().then(() => {
  createWindow();
  app.on('activate', function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', function () {
  if (process.platform !== 'darwin') app.quit();
});