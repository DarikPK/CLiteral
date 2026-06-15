package com.example.imageextractor

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val profilesCollection = db.collection("registrar_profiles")
    private val userProfilesCollection = db.collection("user_profiles")

    private suspend fun ensureAuthenticated() {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            throw Exception("Fallo en la autenticación de Firebase: ${e.localizedMessage}")
        }
    }

    /**
     * Guarda o actualiza el perfil de un registrador en Firestore.
     */
    suspend fun saveProfile(profile: RegistrarProfile): Result<Unit> {
        return try {
            ensureAuthenticated()
            val docRef = if (profile.id.isEmpty()) {
                profilesCollection.document()
            } else {
                profilesCollection.document(profile.id)
            }

            // Asignar el ID generado si es nuevo
            val profileToSave = if (profile.id.isEmpty()) {
                profile.copy(id = docRef.id)
            } else {
                profile
            }

            docRef.set(profileToSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene todos los perfiles guardados.
     */
    suspend fun getAllProfiles(): Result<List<RegistrarProfile>> {
        return try {
            ensureAuthenticated()
            val snapshot = profilesCollection.get().await()
            val profiles = snapshot.toObjects(RegistrarProfile::class.java)
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un perfil de la nube.
     */
    suspend fun deleteProfile(profileId: String): Result<Unit> {
        return try {
            ensureAuthenticated()
            profilesCollection.document(profileId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Guarda o actualiza un UserProfile en Firestore.
     */
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            ensureAuthenticated()
            val docRef = if (profile.id.isEmpty()) {
                userProfilesCollection.document()
            } else {
                userProfilesCollection.document(profile.id)
            }

            val toSave = if (profile.id.isEmpty()) profile.copy(id = docRef.id) else profile
            docRef.set(toSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene todos los UserProfiles guardados.
     */
    suspend fun getAllUserProfiles(): Result<List<UserProfile>> {
        return try {
            ensureAuthenticated()
            val snapshot = userProfilesCollection.get().await()
            val profiles = snapshot.toObjects(UserProfile::class.java)
            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un UserProfile.
     */
    suspend fun deleteUserProfile(profileId: String): Result<Unit> {
        return try {
            ensureAuthenticated()
            userProfilesCollection.document(profileId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
