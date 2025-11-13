# Raziel
Cryptographic Caching and Intelligent Architecture Android application written in Java with the scope of giving the user the responsibility to secure it's own private files on the user's device
Uses wide variety of mobile encryption algorithms such as AES-256, XChaCha20, Threefish, ...
Using only the android's phone capabilites in terms of hardware and software, nothing extra required and everything is kept local

Raziel's uses caching, Tink optimised algorithms and intelligent adaptive architecture for both software and hardware in order to take advantage of all the resources available.
Besides AES256-GCM and XChaCha20-Poly1305 use Tink's StreamingAead and Aead but for the other algorithms an adapter was created to leverage chunking/streaming for better results based on device's hardware/software.

Including:
- Simplified encryption options such as: Just Encrypt My Files | Show Me Some Options | Let Me Fully Customise It
- File Analysis and Tiered Encryption choosing best encryption for the specific file identified
- Predictive Encryption by pre-generating keys and caching frequently used encryption parameters
- Context-aware Security by identifying the WiFi, device's state and location and acting accordingly to the context. Using slower but higher security if on public internet, but all these are inactive by default. Must be granted access by user
- Smart Compression by analysing the file compressibility before encryption
- Encryption profiles such as: Traveler Mode | Daily Use | Batch Mode
- Encrypted Search by allowing searching within encrypted files without full decryption
- Automated Encryption rules such as: Encrypt all screenshots automatically | Encrypt financial PDFs when saved to Downloads | Encrypt camera photos after 24 hours
- Smart key rotation by doing gradual re-encryption in the background based on usage patterns



Future Work:
- Cloud Storage Integration to automatically encrypt files before uploading to GoogleDrive/OneDrive/Dropbox and decrypt when downloading from cloud storage
- Secure File Sharing to encrypt files and share via any app (WhatsApp, Email, Telegram, Signal, etc). The recepeint requires Raziel's app and password to decrypt, also expiration can be set on shared files
  


