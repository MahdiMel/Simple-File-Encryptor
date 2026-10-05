# Simple File Encryptor

A lightweight, purely Java-based command-line tool that securely encrypts files using AES-256 in CBC mode, manages Initialization Vectors (IV), and securely offloads encryption keys to a remote PostgreSQL database (Supabase) via JDBC.

## Features

* **AES-256 CBC Encryption:** Built directly with standard `javax.crypto` libraries.
* **Buffer Streaming:** Processes files in small memory chunks, allowing it to encrypt files of any size without causing `OutOfMemory` errors.
* **Cloud Database Key Management:** Encodes binary `SecretKey` data to Base64 and pushes it to PostgreSQL for secure, persistent storage.
* **Zero External Crypto Dependencies:** Uses native Java I/O and Security libraries.

## Prerequisites

* Java 8 or higher
* A PostgreSQL database (e.g., Supabase)
* PostgreSQL JDBC Driver `.jar` added to your IDE/project build path

## Setup Instructions

### 1. Database Setup
Run the following SQL in your PostgreSQL editor to build the schema:

```sql
CREATE SCHEMA file_security;

CREATE TABLE file_security.secure_files (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    filename character varying(255) NOT NULL,
    encryption_key text NOT NULL,
    created_at timestamp with time zone DEFAULT timezone('utc'::text, now()) NOT NULL
);
```

### 2. Environment Variables
Create a `.env` file in the root directory of this project and add your database credentials:

```env
DB_URL=jdbc:postgresql://your-host-url.pooler.supabase.com:5432/postgres
DB_USER=your_username
DB_PASS=your_password
```

### 3. Run the Code
Ensure you have a test file in your project root, then run the `main` method in `FileEncryptor.java`. The console will walk you through the key generation, encryption, database storage, and decryption process.

## Security Notice & Production Scalability

This is a minimalist educational project. To scale this into a production-ready enterprise microservice, the following architectural upgrades should be implemented:
#### Dedicated Key Management Service (KMS):
Instead of storing Base64 keys in a standard relational database, integrate a dedicated KMS (like AWS KMS, Google Cloud KMS, or HashiCorp Vault) which securely manages key rotation, encryption at rest, and strict access policies.

#### Multi-Tenant Architecture:
The database schema must be expanded to include a user_id (UUID) column to strictly map encryption keys to specific user accounts, preventing unauthorized decryption.

#### Collision Prevention via Hashing:
Implement SHA-256 hashing to generate a unique digital fingerprint (file_hash) for each file and use that as the primary database lookup mechanism.

#### Authenticated Encryption (GCM Mode):
Upgrade the cipher from AES/CBC/PKCS5Padding to AES/GCM/NoPadding. Galois/Counter Mode (GCM) includes built-in authentication (an auth tag) to verify that the ciphertext has not been tampered with or corrupted before the decryption phase begins.
