/* * chatbot.js - API INTEGRATION & SESSION PERSISTENCE VERSION
 * Handles UI, API communication, multi-turn history, and session state.
 */
const HISTORY_KEY = 'calmpulse_chat_history';
const STRUCTURED_HISTORY_KEY = 'calmpulse_chat_structured_history';

// Helper to save current chat HTML content to session storage
function saveChatHistory() {
    const chatBody = document.getElementById('chatbot-body');
    if (chatBody) {
        sessionStorage.setItem(HISTORY_KEY, chatBody.innerHTML);
    }
}

// Helper to load chat HTML content from session storage
function loadChatHistory(defaultMessage) {
    const savedHistory = sessionStorage.getItem(HISTORY_KEY);
    if (savedHistory && savedHistory.trim().length > 0) {
        return savedHistory;
    }
    return `<div class="message bot-message">${defaultMessage}</div>`;
}

// Helper to retrieve structured messages for conversational context
function getStructuredHistory() {
    try {
        const raw = sessionStorage.getItem(STRUCTURED_HISTORY_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch (e) {
        return [];
    }
}

function pushStructuredHistory(role, text) {
    try {
        const list = getStructuredHistory();
        list.push({ role: role, text: text });
        // Keep last 12 exchanges in session
        if (list.length > 12) {
            list.splice(0, list.length - 12);
        }
        sessionStorage.setItem(STRUCTURED_HISTORY_KEY, JSON.stringify(list));
    } catch (e) {
        console.warn("Unable to save structured chat history:", e);
    }
}

function initializePageChatbot(userRole) {
    const containerElement = document.getElementById('chat-page-container');
    if (!containerElement) {
        console.error("Chat page container not found. Check chat.html structure.");
        return;
    }

    const roleName = (userRole && userRole.toLowerCase() === 'employee') ? 'workplace professional' : 'student';
    const initialMessage = `Hello! I'm Pulse Bot, your CalmPulse wellness companion. I see you are a ${roleName}. I can provide supportive guidance on stress reduction, study/workload balance, and mindfulness techniques. How are you feeling today?`;

    // ----------------------------------------------------
    // I. Inject Chat UI into dedicated container
    // ----------------------------------------------------
    const chatUIHTML = `
        <div class="chatbot-container-full" id="chatbot-container">
            <div class="chatbot-header d-flex justify-content-between align-items-center">
                <span>Pulse Bot 🤖 <small class="fw-normal fs-6 text-light opacity-75">| Mental Wellness Assistant</small></span>
                <button id="clear-chat-btn" class="btn btn-sm btn-outline-light py-0 px-2" style="font-size: 0.8rem;" title="Clear Conversation">Reset</button>
            </div>
            <div class="chatbot-body" id="chatbot-body">
            </div>
            <div class="chatbot-footer">
                <input type="text" id="chatbot-input" placeholder="Ask about stress, burnout, focus tips..." class="full-width-input" autocomplete="off">
                <button id="chatbot-send-btn">Send</button>
            </div>
        </div>
    `;

    containerElement.innerHTML = chatUIHTML;

    // ----------------------------------------------------
    // II. DOM References and Load History
    // ----------------------------------------------------
    const chatbotBody = document.getElementById('chatbot-body');
    const chatbotInput = document.getElementById('chatbot-input');
    const chatbotSendBtn = document.getElementById('chatbot-send-btn');
    const clearChatBtn = document.getElementById('clear-chat-btn');
    let isTyping = false;

    // Load existing history or default welcome message
    chatbotBody.innerHTML = loadChatHistory(initialMessage);

    const scrollToBottom = () => {
        chatbotBody.scrollTop = chatbotBody.scrollHeight;
    };

    const appendMessage = (text, sender) => {
        const msgEl = document.createElement('div');
        msgEl.classList.add('message', `${sender}-message`);

        // Format basic bullets / linebreaks cleanly
        if (typeof text === 'string' && (text.includes('\n') || text.includes('•'))) {
            const formatted = text
                .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
                .replace(/\n/g, '<br>')
                .replace(/•\s/g, '• ');
            msgEl.innerHTML = formatted;
        } else {
            msgEl.textContent = text;
        }

        chatbotBody.appendChild(msgEl);
        scrollToBottom();
        saveChatHistory();
    };

    // Reset Chat action
    if (clearChatBtn) {
        clearChatBtn.addEventListener('click', () => {
            sessionStorage.removeItem(HISTORY_KEY);
            sessionStorage.removeItem(STRUCTURED_HISTORY_KEY);
            chatbotBody.innerHTML = `<div class="message bot-message">${initialMessage}</div>`;
            saveChatHistory();
        });
    }

    // ----------------------------------------------------
    // III. API Request Function
    // ----------------------------------------------------
    async function getApiResponse(userMessage) {
        if (isTyping) return;
        isTyping = true;

        // Disable input and show thinking indicator
        chatbotInput.disabled = true;
        chatbotSendBtn.disabled = true;
        appendMessage('Pulse Bot is thinking...', 'bot');
        const typingIndicator = chatbotBody.lastChild;

        const currentBaseUrl = (typeof window.getApiBaseUrl === 'function')
            ? window.getApiBaseUrl()
            : "https://calmpulsebackend.onrender.com";

        const currentUserId = localStorage.getItem("user_id") || "";
        const historyList = getStructuredHistory();

        try {
            const response = await fetch(`${currentBaseUrl}/chat_api`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({
                    message: userMessage,
                    user_role: userRole || 'student',
                    user_id: currentUserId,
                    history: historyList
                })
            });

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                throw new Error(errData.error || `Server error: ${response.statusText}`);
            }

            const data = await response.json();
            const botReply = data.response || "I'm here to listen. How can I assist you with your stress today?";

            // Remove typing indicator and append bot reply
            if (chatbotBody.contains(typingIndicator)) {
                chatbotBody.removeChild(typingIndicator);
            }
            appendMessage(botReply, 'bot');
            pushStructuredHistory('model', botReply);

        } catch (error) {
            console.error('Chat API Error:', error);
            if (chatbotBody.contains(typingIndicator)) {
                chatbotBody.removeChild(typingIndicator);
            }
            appendMessage("I had trouble connecting to the CalmPulse AI service. Please check your internet connection and try again.", 'bot');
        } finally {
            chatbotInput.disabled = false;
            chatbotSendBtn.disabled = false;
            chatbotInput.focus();
            isTyping = false;
        }
    }

    // ----------------------------------------------------
    // IV. Main Send Logic and Event Listeners
    // ----------------------------------------------------
    function sendMessage() {
        const userText = chatbotInput.value.trim();
        if (userText === '' || isTyping) return;

        appendMessage(userText, 'user');
        pushStructuredHistory('user', userText);
        chatbotInput.value = '';
        getApiResponse(userText);
    }

    chatbotSendBtn.addEventListener('click', (e) => {
        e.preventDefault();
        sendMessage();
    });

    chatbotInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            sendMessage();
        }
    });

    window.addEventListener('beforeunload', saveChatHistory);
}
