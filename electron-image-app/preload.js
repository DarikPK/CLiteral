const { contextBridge, ipcRenderer } = require('electron');

// Exponemos de forma segura un objeto 'api' en el contexto global de la ventana del renderizador.
// Esto nos permitirá comunicarnos entre el proceso de renderizado (frontend) y el principal (backend).
contextBridge.exposeInMainWorld('api', {
  // Función para enviar mensajes desde el renderizador al principal.
  send: (channel, data) => {
    // Lista blanca de canales seguros.
    let validChannels = ['download-image', 'extract-images', 'modify-image', 'generate-pdf'];
    if (validChannels.includes(channel)) {
      ipcRenderer.send(channel, data);
    }
  },
  // Función para recibir mensajes desde el principal al renderizador.
  receive: (channel, func) => {
    let validChannels = ['image-downloaded', 'image-modified', 'pdf-generated'];
    if (validChannels.includes(channel)) {
      // Elimina cualquier listener anterior para este canal para evitar duplicados.
      ipcRenderer.removeAllListeners(channel);
      // Añade el nuevo listener.
      ipcRenderer.on(channel, (event, ...args) => func(...args));
    }
  }
});