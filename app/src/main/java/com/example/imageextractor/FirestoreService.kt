package com.example.imageextractor

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Un objeto singleton para gestionar todas las operaciones con Firebase Firestore.
 * Centraliza el acceso a la base de datos para una mayor organización y mantenibilidad.
 */
object FirestoreService {

    private const val REGISTRADORES_COLLECTION = "registradores"

    // Obtiene una instancia de la base de datos de Firestore.
    private val db: FirebaseFirestore by lazy {
        Firebase.firestore
    }

    /**
     * Añade un nuevo documento de registrador a la colección "registradores".
     * @param registrador El objeto Registrador que se va a añadir.
     * @return El ID del documento recién creado en caso de éxito, o null si falla.
     */
    suspend fun addRegistrador(registrador: Registrador): String? {
        return try {
            val documentReference = db.collection(REGISTRADORES_COLLECTION)
                .add(registrador)
                .await()
            documentReference.id
        } catch (e: Exception) {
            // Manejar la excepción (e.g., logging)
            null
        }
    }

    /**
     * Obtiene todos los registradores de la base de datos.
     * @return Una lista de objetos Registrador. La lista estará vacía si no hay ninguno o si ocurre un error.
     */
    suspend fun getRegistradores(): List<Registrador> {
        return try {
            val snapshot = db.collection(REGISTRADORES_COLLECTION)
                .get()
                .await()
            // Convierte cada documento del snapshot a un objeto Registrador.
            snapshot.toObjects(Registrador::class.java)
        } catch (e: Exception) {
            // Manejar la excepción (e.g., logging)
            emptyList()
        }
    }

    /**
     * Comprueba si la colección de registradores está vacía.
     * @return true si no hay ningún registrador en la base de datos, false en caso contrario.
     */
    suspend fun isRegistradoresCollectionEmpty(): Boolean {
        return try {
            val snapshot = db.collection(REGISTRADORES_COLLECTION)
                .limit(1)
                .get()
                .await()
            snapshot.isEmpty
        } catch (e: Exception) {
            // En caso de error, asumimos que está vacía para evitar comportamientos inesperados.
            true
        }
    }

    /**
     * Obtiene un único registrador por su ID.
     * @param registradorId El ID del documento a obtener.
     * @return El objeto Registrador si se encuentra, o null si no existe o hay un error.
     */
    suspend fun getRegistrador(registradorId: String): Registrador? {
        return try {
            val document = db.collection(REGISTRADORES_COLLECTION)
                .document(registradorId)
                .get()
                .await()
            document.toObject(Registrador::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Actualiza un documento de registrador existente en Firestore.
     * @param registrador El objeto Registrador con los datos actualizados. Su ID debe ser válido.
     * @return true si la operación fue exitosa, false en caso contrario.
     */
    suspend fun updateRegistrador(registrador: Registrador): Boolean {
        return try {
            db.collection(REGISTRADORES_COLLECTION)
                .document(registrador.id)
                .set(registrador)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Elimina un registrador de la base de datos por su ID.
     * @param registradorId El ID del documento a eliminar.
     * @return true si la operación fue exitosa, false en caso contrario.
     */
    suspend fun deleteRegistrador(registradorId: String): Boolean {
        return try {
            db.collection(REGISTRADORES_COLLECTION)
                .document(registradorId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
