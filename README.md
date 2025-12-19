# Local LLM Assistant for Burp Suite

A privacy-focused, offline AI assistant integrated directly into Burp Suite. This extension connects Burp Suite to a local LLM (via Ollama) to assist with penetration testing, vulnerability analysis, and payload generation without sending any data to the cloud.

## Features

* **100% Private & Offline:** All data stays on your machine (localhost). No API keys, no cloud fees.
* **Red Team Persona:** The AI is prompted to act as a sarcastic, technical Bug Bounty Hunter. It skips defensive advice and focuses on exploitation and PoCs.
* **Integrated UI:**
    * **Dark Mode Support:** Python/Shell code blocks are rendered in dark gray boxes for easy reading.
    * **Chat Interface:** A dedicated "Local AI" tab to chat with the model and keep a history of your analysis.
* **Context-Aware:**
    * **Popup Prompt:** Right-click any request and select *"Ask Local AI..."* to instantly send specific questions (e.g., "Check parameter 'q' for SQLi").
    * **Smart Routing:** The AI ignores the "Hacker" persona if you are just chatting (e.g., "hello" or "stop"), preventing unwanted lecture spam.

## Prerequisites

Before running the extension, ensure you have the following installed:

1.  **Burp Suite Professional / Community**
2.  **Java Development Kit (JDK) 17+**
3.  **[Ollama](https://ollama.com/)** (The local AI server)

## Model Setup

This extension is hardcoded to use the **Dolphin-Mistral** model (an uncensored model ideal for security testing).

1.  Install Ollama.
2.  Pull the specific model:
    ```bash
    ollama pull dolphin-mistral
    ```
3.  Start the server (usually runs automatically, but you can force it):
    ```bash
    ollama serve
    ```
    *Ensure it is running on `http://localhost:11434` (default).*

## Installation & Build

1.  **Clone/Download** this repository.
2.  **Build the JAR** using Gradle:
    * *Using IntelliJ:* Open the Gradle tab -> Tasks -> Build -> `jar`.
    * *Using Terminal:*
        ```bash
        ./gradlew jar
        ```
3.  **Load into Burp Suite:**
    * Go to **Extensions** -> **Installed**.
    * Click **Add**.
    * Select **Java** as the extension type.
    * Select the built JAR file (usually found in `build/libs/`).

## Usage

### Method 1: The "Quick Ask" (Popup)
1.  Go to **Proxy** or **Repeater**.
2.  **Right-click** on any HTTP Request.
3.  Select **Ask Local AI...**.
4.  Type your question (e.g., *"Write a Python script to fuzz the ID parameter"*).
5.  The extension will switch to the **Local AI** tab and generate the response.

### Method 2: The "Chat" Tab
1.  Click the **Local AI** tab in the main Burp Suite bar.
2.  If you have previously sent a request, it is loaded in context.
3.  Type any follow-up question in the bottom text box and click **Ask AI**.

## Privacy Note
This tool sends HTTP requests from Burp Suite to `http://localhost:11434`.
* **No data** leaves your local network.
* **No data** is sent to OpenAI, Anthropic, or Google.
* Ideal for analyzing sensitive targets or PII where cloud-based AI is prohibited.

## Disclaimer
This tool is intended for **legal security research and authorized penetration testing only**. The "Dolphin" model is uncensored and may generate harmful content if prompted maliciously. Use responsibly.
