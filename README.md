# Bug Hunter's Copilot for Burp Suite

**Turn your Local LLM into a Senior Security Analyst.**

Bug Hunter's Copilot is a privacy-focused Burp Suite extension that uses a local AI (via Ollama) to analyze HTTP traffic. Instead of trying to "hack" the target for you, it helps *you* understand the target faster. It acts as a sidecar analyst: explaining complex request logic, identifying theoretical risks, and drafting professional vulnerability reports all without sending a single byte of data to the cloud.

## Key Features

* **100% Private & Sanitized:**
    * All analysis happens on `localhost`.
    * **Auto-Redaction:** Automatically strips sensitive headers (`Authorization`, `Cookie`, `X-API-Key`) *before* the data touches the LLM, keeping your client's secrets safe.

* **Dual-Mode Intelligence:**
    * **Analyst Mode:** Breaks down the "Anatomy" of a request. Identifies user-controlled inputs, state tokens, and suggests high-level logic flaws (IDOR, Mass Assignment, etc.).
    * **Reporter Mode:** Drafts a professional-grade vulnerability report (Title, Impact, Remediation) based on the current context, saving you 15-20 minutes of writing time per bug.

* **Full Context Awareness:** Analyzes both the **Request** and **Response**. It can detect reflected input (XSS clues), error leakage, and status code logic.

* **Structured HTML Output:** No more walls of text. The AI generates clean, formatted HTML reports with bullet points and headers directly inside the Burp UI.

## Prerequisites

1.  **Burp Suite Professional / Community**
2.  **Java Development Kit (JDK) 17+**
3.  **[Ollama](https://ollama.com/)** (The local AI server)

## Model Setup

We recommend **Dolphin-Mistral** or **Llama 3** for the best balance of speed and reasoning.

1.  Install Ollama.
2.  Pull a model:
    ```bash
    ollama pull dolphin-mistral
    ```
3.  Start the server:
    ```bash
    ollama serve
    ```
    *Ensure it is running on `http://localhost:11434` (default).*

## Installation

1.  **Clone** this repository.
2.  **Build the JAR**:
    ```bash
    ./gradlew jar
    ```
3.  **Load into Burp Suite**:
    * Go to **Extensions** -> **Installed**.
    * Click **Add**.
    * Select the `.jar` file from `build/libs/`.

## Workflow

### 1. Send to Copilot
Right-click any request in **Proxy** or **Repeater** and select **Send to Copilot**.

### 2. Review Context
Go to the **Copilot** tab. You will see your request (and response, if available) in the "Sanitized Context" panel. Note that sensitive tokens have been replaced with `[REDACTED]`.

### 3. Choose Action
* **[ Analyze Full Context ]**: Click this to understand *what* the endpoint does. The AI will explain the data flow and hypothesize risks (e.g., "The `user_id` parameter is present but no session token validation was found in the response logic").
* **[ Draft Finding Report ]**: Click this once you have confirmed a bug. The AI will write a "Title, Description, Impact, Remediation" draft for you to copy-paste into HackerOne or your final report.

## Privacy & Safety
* **Local Only:** HTTP requests are sent strictly to `localhost:11434`. No data leaves your machine.
* **Sanitization:** The extension actively strips `Cookie`, `Authorization`, and `X-API-Key` headers before analysis. This ensures that even if you swap models, PII and credentials are not exposed to the context window history.

<img width="1912" height="1000" alt="image" src="https://github.com/user-attachments/assets/baae2346-4cd5-4310-b4f2-bf655bafe135" />

## Disclaimer
This tool is intended for **legal security research and authorized penetration testing only**. It is designed to assist human analysts, not replace them. Use responsibly.
