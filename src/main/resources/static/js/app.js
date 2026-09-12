/* ================================================================
   AI Shopping Agent — Frontend Application
   Handles: Auth, Chat, SSE Streaming, Product Cards
   ================================================================ */

const API_BASE = '';  // Same origin (Spring Boot serves frontend)

// ================================================================
// State
// ================================================================
let state = {
    token: localStorage.getItem('token') || null,
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    currentConversationId: null,
    conversations: [],
    messages: [],
    isProcessing: false,
};

// ================================================================
// DOM References
// ================================================================
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

// ================================================================
// API Client
// ================================================================
async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`;

    const res = await fetch(`${API_BASE}${path}`, { ...options, headers: { ...headers, ...options.headers } });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;

    if (!res.ok) {
        throw { status: res.status, message: data?.message || 'Something went wrong' };
    }
    return data;
}

// ================================================================
// Auth
// ================================================================
function initAuth() {
    $('#login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = $('#login-email').value;
        const password = $('#login-password').value;
        $('#login-error').textContent = '';
        $('#login-btn').disabled = true;

        try {
            const data = await api('/api/auth/login', {
                method: 'POST',
                body: JSON.stringify({ email, password }),
            });
            loginSuccess(data);
        } catch (err) {
            $('#login-error').textContent = err.message || 'Login failed';
        } finally {
            $('#login-btn').disabled = false;
        }
    });

    $('#register-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = $('#register-name').value;
        const email = $('#register-email').value;
        const password = $('#register-password').value;
        $('#register-error').textContent = '';
        $('#register-btn').disabled = true;

        try {
            await api('/api/auth/register', {
                method: 'POST',
                body: JSON.stringify({ name, email, password }),
            });
            const loginData = await api('/api/auth/login', {
                method: 'POST',
                body: JSON.stringify({ email, password }),
            });
            loginSuccess(loginData);
        } catch (err) {
            $('#register-error').textContent = err.message || 'Registration failed';
        } finally {
            $('#register-btn').disabled = false;
        }
    });

    $('#show-register').addEventListener('click', (e) => {
        e.preventDefault();
        $('#login-form').style.display = 'none';
        $('#register-form').style.display = 'block';
    });

    $('#show-login').addEventListener('click', (e) => {
        e.preventDefault();
        $('#register-form').style.display = 'none';
        $('#login-form').style.display = 'block';
    });

    $('#logout-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        logout();
    });

    // Check if already logged in
    if (state.token && state.user) {
        showApp();
    }
}

function loginSuccess(data) {
    const token = data.accessToken || data.token;
    const user = data.user || { name: data.name, email: data.email, id: data.userId || data.id };
    state.token = token;
    state.user = user;
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    showApp();
}

function logout() {
    state.token = null;
    state.user = null;
    state.currentConversationId = null;
    state.conversations = [];
    state.messages = [];
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    showAuth();
}

function showApp() {
    $('#auth-overlay').style.display = 'none';
    $('#app').style.display = 'flex';
    updateUserDisplay();
    loadConversations();
}

function showAuth() {
    $('#auth-overlay').style.display = 'flex';
    $('#app').style.display = 'none';
    $('#login-form').style.display = 'block';
    $('#register-form').style.display = 'none';
}

function updateUserDisplay() {
    if (state.user) {
        const initial = (state.user.name || state.user.email || 'U')[0].toUpperCase();
        $('#user-avatar').textContent = initial;
        $('#user-name').textContent = state.user.name || state.user.email;
        $('#greeting-name').textContent = (state.user.name || 'there').split(' ')[0];
    }
}

// ================================================================
// Conversations
// ================================================================
async function loadConversations() {
    try {
        state.conversations = await api('/api/conversations');
        renderConversations();
    } catch (err) {
        if (err.status === 401) logout();
        console.error('Failed to load conversations:', err);
    }
}

function renderConversations() {
    const container = $('#conversations-list');
    container.innerHTML = '';

    (state.conversations || []).forEach(conv => {
        const btn = document.createElement('button');
        btn.className = 'conv-item' + (conv.id === state.currentConversationId ? ' active' : '');
        btn.textContent = conv.title || 'New Chat';
        btn.addEventListener('click', () => selectConversation(conv.id));
        container.appendChild(btn);
    });
}

async function selectConversation(id) {
    state.currentConversationId = id;
    renderConversations();

    try {
        const messages = await api(`/api/conversations/${id}/messages`);
        state.messages = messages;
        renderMessages();
    } catch (err) {
        if (err.status === 401) logout();
        console.error('Failed to load messages:', err);
    }
}

async function createConversation() {
    try {
        const conv = await api('/api/conversations', {
            method: 'POST',
            body: JSON.stringify({ title: 'New Chat' }),
        });
        state.conversations.unshift(conv);
        state.currentConversationId = conv.id;
        state.messages = [];
        renderConversations();
        renderMessages();
        return conv.id;
    } catch (err) {
        if (err.status === 401) logout();
        console.error('Failed to create conversation:', err);
        return null;
    }
}

// ================================================================
// Chat
// ================================================================
function initChat() {
    const form = $('#chat-form');
    const input = $('#chat-input');
    const sendBtn = $('#send-btn');

    // Auto-resize textarea
    input.addEventListener('input', () => {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 150) + 'px';
        sendBtn.disabled = !input.value.trim();
    });

    // Enter to send (Shift+Enter for newline)
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            if (input.value.trim() && !state.isProcessing) {
                form.dispatchEvent(new Event('submit'));
            }
        }
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const text = input.value.trim();
        if (!text || state.isProcessing) return;

        input.value = '';
        input.style.height = 'auto';
        sendBtn.disabled = true;

        await sendMessage(text);
    });

    // Suggestion chips
    $$('.chip, .try-chip').forEach(btn => {
        btn.addEventListener('click', () => {
            const query = btn.dataset.query;
            if (query && !state.isProcessing) sendMessage(query);
        });
    });

    // New Chat
    $('#new-chat-btn').addEventListener('click', () => {
        state.currentConversationId = null;
        state.messages = [];
        renderMessages();
        renderConversations();
        input.focus();
    });
}

async function sendMessage(text) {
    state.isProcessing = true;
    updateUIState();

    // Ensure we have a conversation
    if (!state.currentConversationId) {
        const id = await createConversation();
        if (!id) {
            state.isProcessing = false;
            updateUIState();
            return;
        }
    }

    // Add user message to UI
    state.messages.push({ role: 'USER', content: text });
    renderMessages();

    // Show progress
    showProgress('Analyzing your request...');

    try {
        // Use SSE streaming endpoint
        await streamChat(state.currentConversationId, text);

        // Update conversation title with first message
        if (state.messages.length <= 2) {
            updateConversationTitle(state.currentConversationId, text.slice(0, 50));
        }
    } catch (err) {
        console.error('Chat error:', err);
        if (err.status === 401) {
            logout();
            return;
        }
        const lastMsg = state.messages[state.messages.length - 1];
        if (!lastMsg || lastMsg.role === 'USER') {
            state.messages.push({ role: 'ASSISTANT', content: 'Something went wrong. Please try again.' });
            renderMessages();
        }
    } finally {
        hideProgress();
        state.isProcessing = false;
        updateUIState();
        loadConversations();
    }
}

async function streamChat(conversationId, message) {
    return new Promise((resolve, reject) => {
        const body = JSON.stringify({ conversationId, message });
        let receivedResult = false;

        fetch(`${API_BASE}/api/chat/stream`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${state.token}`,
            },
            body: body,
        }).then(response => {
            if (!response.ok) {
                // Fallback to sync endpoint
                return fallbackSyncChat(conversationId, message).then(resolve).catch(reject);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            function read() {
                reader.read().then(({ done, value }) => {
                    if (done) {
                        resolve();
                        return;
                    }

                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop(); // Keep incomplete line in buffer

                    for (const line of lines) {
                        if (line.startsWith('data:')) {
                            const dataStr = line.slice(5).trim();
                            if (!dataStr) continue;
                            try {
                                const event = JSON.parse(dataStr);
                                if (event.type === 'RESULT') {
                                    receivedResult = true;
                                }
                                handleSSEEvent(event);
                            } catch (e) {
                                // Ignore parse errors
                            }
                        }
                    }

                    read();
                }).catch(err => {
                    if (receivedResult) {
                        resolve();
                    } else {
                        reject(err);
                    }
                });
            }

            read();
        }).catch((err) => {
            if (receivedResult) {
                resolve();
            } else {
                fallbackSyncChat(conversationId, message).then(resolve).catch(reject);
            }
        });
    });
}

async function fallbackSyncChat(conversationId, message) {
    const data = await api('/api/chat', {
        method: 'POST',
        body: JSON.stringify({ conversationId, message }),
    });
    handleChatResponse(data);
}

function handleSSEEvent(event) {
    switch (event.type) {
        case 'STATUS':
            showProgress(event.message || 'Processing...');
            break;
        case 'RESULT':
            handleChatResponse(event.data);
            break;
        case 'ERROR':
            state.messages.push({ role: 'ASSISTANT', content: event.message || 'Something went wrong.' });
            renderMessages();
            break;
        case 'DONE':
            hideProgress();
            break;
    }
}

function handleChatResponse(data) {
    if (!data) return;

    // Add assistant message
    state.messages.push({
        role: 'ASSISTANT',
        content: data.message,
        products: data.products,
        comparison: data.comparison,
        status: data.status,
    });

    renderMessages();
}

function updateConversationTitle(id, title) {
    api(`/api/conversations/${id}`, {
        method: 'PUT',
        body: JSON.stringify({ title }),
    }).catch(() => {}); // Silent fail
}

// ================================================================
// Rendering
// ================================================================
function renderMessages() {
    const container = $('#chat-messages');
    const emptyState = $('#empty-state');
    const tryAsking = $('#try-asking');

    if (state.messages.length === 0) {
        emptyState.style.display = 'flex';
        tryAsking.style.display = 'flex';
        // Clear any rendered messages but keep empty state
        const children = Array.from(container.children);
        children.forEach(child => {
            if (child !== emptyState) child.remove();
        });
        return;
    }

    emptyState.style.display = 'none';
    tryAsking.style.display = 'none';

    // Clear and re-render
    const children = Array.from(container.children);
    children.forEach(child => {
        if (child !== emptyState) child.remove();
    });

    state.messages.forEach(msg => {
        const el = createMessageElement(msg);
        container.appendChild(el);
    });

    // Scroll to bottom
    requestAnimationFrame(() => {
        container.scrollTop = container.scrollHeight;
    });
}

function createMessageElement(msg) {
    const wrapper = document.createElement('div');
    wrapper.className = `message ${msg.role === 'USER' ? 'user' : 'assistant'}`;

    if (msg.role === 'ASSISTANT') {
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = '✦';
        wrapper.appendChild(avatar);
    }

    const content = document.createElement('div');
    content.className = 'message-content';

    if (msg.role === 'USER') {
        content.textContent = msg.content;
    } else {
        content.innerHTML = renderMarkdown(msg.content || '');

        // Render product cards
        if (msg.products && msg.products.length > 0) {
            const cards = createProductCards(msg.products);
            content.appendChild(cards);
        }

        // Voice read aloud button
        if (msg.content && msg.content.trim()) {
            const actions = document.createElement('div');
            actions.className = 'message-actions';
            const speakBtn = document.createElement('button');
            speakBtn.className = 'btn-speak';
            speakBtn.title = 'Listen to recommendations';
            speakBtn.innerHTML = '🔊 <span>Read aloud</span>';
            speakBtn.addEventListener('click', () => toggleSpeech(msg.content, speakBtn));
            actions.appendChild(speakBtn);
            content.appendChild(actions);
        }
    }

    wrapper.appendChild(content);
    return wrapper;
}

function createProductCards(products) {
    const container = document.createElement('div');
    container.className = 'product-cards';

    products.forEach(product => {
        const card = document.createElement('div');
        card.className = 'product-card';

        // Top section with thumbnail and main details
        const topSection = document.createElement('div');
        topSection.className = 'product-card-top';

        // Thumbnail
        if (product.imageUrl) {
            const thumb = document.createElement('div');
            thumb.className = 'product-thumb';
            const img = document.createElement('img');
            img.className = 'product-thumb-img';
            img.src = product.imageUrl;
            img.alt = product.name;
            img.loading = 'lazy';
            img.onerror = () => { thumb.style.display = 'none'; };
            thumb.appendChild(img);
            topSection.appendChild(thumb);
        }

        const main = document.createElement('div');
        main.className = 'product-card-main';

        // Header
        const header = document.createElement('div');
        header.className = 'product-card-header';

        const info = document.createElement('div');
        info.className = 'product-card-info';

        const name = document.createElement('div');
        name.className = 'product-card-name';
        name.textContent = product.name;
        info.appendChild(name);

        if (product.brand) {
            const brand = document.createElement('div');
            brand.className = 'product-card-brand';
            brand.textContent = product.brand;
            info.appendChild(brand);
        }

        header.appendChild(info);

        if (product.badge) {
            const badge = document.createElement('span');
            badge.className = `product-card-badge badge-${product.badge}`;
            badge.textContent = formatBadge(product.badge);
            header.appendChild(badge);
        }

        main.appendChild(header);

        // Meta (price, originalPrice, discount, rating, score)
        const meta = document.createElement('div');
        meta.className = 'product-card-meta';

        if (product.bestPrice != null) {
            const price = document.createElement('span');
            price.className = 'product-price';
            price.textContent = `₹${Math.round(product.bestPrice).toLocaleString('en-IN')}`;
            meta.appendChild(price);
        }

        const rep = product.representativeProduct;
        if (rep && rep.originalPrice && rep.originalPrice > (product.bestPrice || 0)) {
            const orig = document.createElement('span');
            orig.className = 'product-original-price';
            orig.textContent = `₹${Math.round(rep.originalPrice).toLocaleString('en-IN')}`;
            meta.appendChild(orig);

            if (rep.discountPercentage) {
                const disc = document.createElement('span');
                disc.className = 'product-discount';
                disc.textContent = `${Math.round(rep.discountPercentage)}% OFF`;
                meta.appendChild(disc);
            }
        }

        if (product.rating != null) {
            const rating = document.createElement('span');
            rating.className = 'product-rating';
            rating.textContent = `${product.rating}★`;
            meta.appendChild(rating);
        }

        if (product.finalScore != null) {
            const score = document.createElement('span');
            score.className = 'product-score';
            score.textContent = `Match: ${Math.round(product.finalScore * 10)}%`;
            meta.appendChild(score);
        }

        main.appendChild(meta);

        // Key specifications chips
        if (rep && rep.specifications && Object.keys(rep.specifications).length > 0) {
            const specsContainer = document.createElement('div');
            specsContainer.className = 'product-specs-chips';
            Object.entries(rep.specifications).slice(0, 4).forEach(([k, v]) => {
                if (v && v !== 'N/A') {
                    const chip = document.createElement('span');
                    chip.className = 'spec-chip';
                    chip.textContent = `${v}`;
                    specsContainer.appendChild(chip);
                }
            });
            if (specsContainer.children.length > 0) {
                main.appendChild(specsContainer);
            }
        }

        topSection.appendChild(main);
        card.appendChild(topSection);

        // Reason
        if (product.reason) {
            const reason = document.createElement('div');
            reason.className = 'product-card-reason';
            reason.textContent = product.reason;
            card.appendChild(reason);
        }

        // Multi-Store Offers
        if (product.offers && product.offers.length > 0) {
            const offers = document.createElement('div');
            offers.className = 'product-offers';

            const title = document.createElement('div');
            title.className = 'product-offers-title';
            title.textContent = `Best deals from verified stores:`;
            offers.appendChild(title);

            product.offers.forEach(offer => {
                const row = document.createElement('div');
                row.className = 'offer-row';

                const store = document.createElement('span');
                store.className = 'offer-store';
                const storeName = offer.store || 'Online Store';
                store.textContent = storeName;
                row.appendChild(store);

                const price = document.createElement('span');
                price.className = 'offer-price';
                price.textContent = offer.price != null ? `₹${Math.round(offer.price).toLocaleString('en-IN')}` : '';
                row.appendChild(price);

                if (offer.productUrl) {
                    const link = document.createElement('a');
                    link.className = 'offer-btn';
                    link.href = offer.productUrl;
                    link.target = '_blank';
                    link.rel = 'noopener noreferrer';
                    const targetStore = storeName.includes('Flipkart') ? 'Flipkart' : (storeName.includes('Croma') ? 'Croma' : 'Amazon');
                    link.textContent = `Buy on ${targetStore} ↗`;
                    row.appendChild(link);
                }

                offers.appendChild(row);
            });

            card.appendChild(offers);
        }

        container.appendChild(card);
    });

    return container;
}

function formatBadge(badge) {
    const badges = {
        'BEST_OVERALL': '🏆 Best Overall',
        'CHEAPEST': '💰 Cheapest',
        'BEST_VALUE': '⭐ Best Value',
        'HIGHEST_RATED': '⭐ Highest Rated',
        'BEST_BATTERY': '🔋 Best Battery',
        'MOST_POPULAR': '🔥 Most Popular',
    };
    return badges[badge] || badge;
}

// Simple markdown → HTML
function renderMarkdown(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.+?)\*/g, '<em>$1</em>')
        .replace(/`(.+?)`/g, '<code>$1</code>')
        .replace(/\n\n/g, '</p><p>')
        .replace(/\n/g, '<br>')
        .replace(/^/, '<p>')
        .replace(/$/, '</p>');
}

// ================================================================
// Progress
// ================================================================
function showProgress(text) {
    const bar = $('#progress-bar');
    const label = $('#progress-text');
    bar.style.display = 'block';
    label.textContent = text;
}

function hideProgress() {
    $('#progress-bar').style.display = 'none';
}

function updateUIState() {
    const input = $('#chat-input');
    const sendBtn = $('#send-btn');
    input.disabled = state.isProcessing;
    sendBtn.disabled = state.isProcessing || !input.value.trim();
    if (!state.isProcessing) input.focus();
}

// ================================================================
// Voice Input & Output (Web Speech API)
// ================================================================
let recognition = null;
let isRecording = false;
let currentUtterance = null;

function initVoice() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    const micBtn = $('#mic-btn');
    const micHint = $('#mic-hint');
    const chatInput = $('#chat-input');
    const sendBtn = $('#send-btn');

    if (!micBtn) return;

    if (!SpeechRecognition) {
        micBtn.title = 'Voice recognition not supported in this browser';
        micBtn.style.opacity = '0.5';
        micBtn.addEventListener('click', () => {
            alert('Voice input is supported in Chrome, Edge, and Safari browsers.');
        });
        return;
    }

    try {
        recognition = new SpeechRecognition();
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.lang = 'en-IN';

        recognition.onstart = () => {
            isRecording = true;
            micBtn.classList.add('recording');
            if (micHint) micHint.style.display = 'inline';
            chatInput.placeholder = 'Listening... Speak now';
        };

        recognition.onresult = (event) => {
            let transcript = '';
            for (let i = event.resultIndex; i < event.results.length; i++) {
                transcript += event.results[i][0].transcript;
            }
            chatInput.value = transcript;
            chatInput.style.height = 'auto';
            chatInput.style.height = Math.min(chatInput.scrollHeight, 150) + 'px';
            sendBtn.disabled = !chatInput.value.trim();
        };

        recognition.onerror = (event) => {
            console.warn('Speech recognition event:', event.error);
            stopRecording();
        };

        recognition.onend = () => {
            stopRecording();
            if (chatInput.value.trim()) {
                chatInput.focus();
            }
        };

        function stopRecording() {
            isRecording = false;
            micBtn.classList.remove('recording');
            if (micHint) micHint.style.display = 'none';
            chatInput.placeholder = 'Ask me anything…';
        }

        micBtn.addEventListener('click', () => {
            if (state.isProcessing) return;
            if (isRecording) {
                recognition.stop();
            } else {
                try {
                    recognition.start();
                } catch (err) {
                    console.error('Failed to start speech recognition:', err);
                }
            }
        });
    } catch (e) {
        console.warn('Could not initialize SpeechRecognition:', e);
    }
}

function toggleSpeech(text, btn) {
    if (!window.speechSynthesis) {
        alert('Text-to-speech is not supported in this browser.');
        return;
    }

    if (window.speechSynthesis.speaking) {
        window.speechSynthesis.cancel();
        btn.classList.remove('speaking');
        btn.innerHTML = '🔊 <span>Read aloud</span>';
        if (currentUtterance && currentUtterance.btn === btn) {
            currentUtterance = null;
            return;
        }
    }

    // Clean markdown formatting for clear narration
    const cleanText = text
        .replace(/\*\*/g, '')
        .replace(/\*/g, '')
        .replace(/#/g, '')
        .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
        .replace(/₹/g, 'Rupees ')
        .replace(/★/g, ' stars ')
        .trim();

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.lang = 'en-IN';
    utterance.rate = 1.0;
    utterance.pitch = 1.0;

    btn.classList.add('speaking');
    btn.innerHTML = '⏹ <span>Stop</span>';
    currentUtterance = { utterance, btn };

    utterance.onend = () => {
        btn.classList.remove('speaking');
        btn.innerHTML = '🔊 <span>Read aloud</span>';
        currentUtterance = null;
    };

    utterance.onerror = () => {
        btn.classList.remove('speaking');
        btn.innerHTML = '🔊 <span>Read aloud</span>';
        currentUtterance = null;
    };

    window.speechSynthesis.speak(utterance);
}

// ================================================================
// Init
// ================================================================
document.addEventListener('DOMContentLoaded', () => {
    initAuth();
    initChat();
    initVoice();
});
