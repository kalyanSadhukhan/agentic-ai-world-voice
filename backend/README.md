<div align="center">
  <h1>📧 VoiceAI Contact Email Backend</h1>
  <p>A robust Spring Boot backend designed for processing and dispatching professional contact form emails.</p>
</div>

---

## 📌 Features

- **Dynamic MimeMessage Generation:** Automatically builds polished HTML email boilerplates directly from internal templates.
- **Smart Reply-To Routing:** Formats strict email validations allowing client replies to seamlessly route to the customer (e.g., `<username>@domain.com`).
- **Data Safety:** Handles partial/empty form inputs gracefully, substituting missing text with `N/A`.
- **Environment Isolation:** Relies entirely on `.env` variables ensuring no static credentials exist natively within the repository.

---

## 🛠 Tech Stack
- **Java 17**
- **Spring Boot 3.2.4**
- **Spring Mail (JavaMailSender)**
- **Maven**
- **io.github.cdimascio:dotenv-java**

---

## 🚦 Endpoints

### 1. Simple Contact
`POST /api/contact/simple`

Handles lightweight contact inquiries.

**Request Payload:** 
```json
{
  "name": "User Name",
  "email": "user@example.com",
  "company": "Company Name",
  "message": "Message text..."
}
```

### 2. Enhanced Inquiry
`POST /api/contact/enhanced`

Handles complex interactions requesting specific AI integrations. 

**Request Payload:** 
```json
{
  "name": "Test User",
  "email": "test@example.com",
  "company": "Test Company",
  "message": "This is a test message",
  "primaryGoal": "Automation",
  "agentTypes": ["AI Chatbot"]
}
```

---

## ⚙️ Setup and Configuration

1. **Clone the repository and enter the internal directory:**
   ```bash
   cd backend
   ```

2. **Configure your environment setup:**
   Duplicate the provided `.env.example` file specifically into `.env`:
   ```bash
   cp .env.example .env
   ```

3. **Populate the `.env` variables:**
   ```properties
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   EMAIL_USER=your_gmail_address@gmail.com
   EMAIL_PASS=your_google_app_password
   RECEIVER_EMAIL=your_receiver_email@domain.com
   ```

---

## 🚀 Running Locally

Execute the standard Maven runner wrapper to bootstrap the internal Tomcat server:

```bash
mvn spring-boot:run
```

The Spring application will instantly secure endpoints onto `http://localhost:8081`. 
