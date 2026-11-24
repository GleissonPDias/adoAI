(function () {
    'use strict';

    const ICONS = {
        spark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3 1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 17v4M21 19h-4"/></svg>',
        message: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
        clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>',
        trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6"/></svg>',
        alert: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>'
    };

    let chatId = null;

    const messagesEl = document.getElementById('messages');
    const chatListEl = document.getElementById('chatList');
    const chatCountEl = document.getElementById('chatCount');
    const inputEl = document.getElementById('messageInput');
    const composerForm = document.getElementById('composerForm');
    const sendBtn = document.getElementById('sendBtn');
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    const heroTemplate = document.getElementById('heroTemplate');

    function icon(name) {
        const el = document.createElement('span');
        el.innerHTML = ICONS[name];
        return el.firstElementChild;
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function clearMessages() {
        messagesEl.innerHTML = '';
    }

    function addMessage(text, cls, time) {
        const isUser = cls === 'user';
        const isError = cls === 'error';

        const row = document.createElement('div');
        row.className = 'msg-row ' + (isUser ? 'user' : isError ? 'error' : 'bot');

        if (isUser) {
            const bubble = document.createElement('div');
            bubble.className = 'bubble user-bubble';
            bubble.textContent = text;
            row.appendChild(bubble);
        } else {
            const avatar = document.createElement('div');
            avatar.className = 'msg-avatar';
            avatar.setAttribute('aria-hidden', 'true');
            avatar.appendChild(icon(isError ? 'alert' : 'spark'));

            const content = document.createElement('div');
            content.className = 'msg-content';

            const header = document.createElement('div');
            header.className = 'msg-header';

            const author = document.createElement('span');
            author.className = 'msg-author';
            author.textContent = isError ? 'Atenção' : 'AdoAI';

            const timeEl = document.createElement('span');
            timeEl.className = 'msg-time';
            timeEl.textContent = formatTime(time);

            header.appendChild(author);
            header.appendChild(timeEl);

            const bubble = document.createElement('div');
            bubble.className = 'bubble ' + (isError ? 'error-bubble' : 'bot-bubble');
            bubble.textContent = text;

            content.appendChild(header);
            content.appendChild(bubble);
            row.appendChild(avatar);
            row.appendChild(content);
        }

        messagesEl.appendChild(row);
        scrollToBottom();
        return row;
    }

    function showEmpty() {
        clearMessages();
        const hero = heroTemplate.content.cloneNode(true);
        messagesEl.appendChild(hero);
    }

    function showTyping(show) {
        let el = document.getElementById('typing');
        if (show && !el) {
            el = document.createElement('div');
            el.id = 'typing';
            el.className = 'typing-row';

            const avatar = document.createElement('div');
            avatar.className = 'msg-avatar';
            avatar.setAttribute('aria-hidden', 'true');
            avatar.appendChild(icon('spark'));

            const card = document.createElement('div');
            card.className = 'typing-card';

            const label = document.createElement('div');
            label.className = 'typing-label';
            label.textContent = 'Consultando o universo...';

            const wave = document.createElement('div');
            wave.className = 'typing-wave';
            for (let i = 0; i < 6; i++) {
                wave.appendChild(document.createElement('span'));
            }

            const line = document.createElement('div');
            line.className = 'typing-line';

            card.appendChild(label);
            card.appendChild(wave);
            card.appendChild(line);

            el.appendChild(avatar);
            el.appendChild(card);
            messagesEl.appendChild(el);
            scrollToBottom();
        } else if (!show && el) {
            el.remove();
        }
    }

    function formatDate(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (isNaN(d)) return '';
        return d.toLocaleDateString('pt-BR') + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }

    function formatTime(iso) {
        if (!iso) return new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
        const d = new Date(iso);
        if (isNaN(d)) return '';
        return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    }

    function renderChatSkeleton() {
        chatListEl.innerHTML = '';
        for (let i = 0; i < 3; i++) {
            const item = document.createElement('div');
            item.className = 'skeleton-item';
            chatListEl.appendChild(item);
        }
    }

    async function loadChats() {
        renderChatSkeleton();
        try {
            const res = await fetch('/api/chat');
            if (!res.ok) {
                chatListEl.innerHTML = '';
                return;
            }
            const chats = await res.json();
            renderChatList(chats);
        } catch (err) {
            chatListEl.innerHTML = '';
        }
    }

    function renderChatList(chats) {
        chatListEl.innerHTML = '';
        chatCountEl.textContent = String(chats ? chats.length : 0);

        if (!chats || chats.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'chat-list-empty';
            empty.appendChild(icon('message'));
            const title = document.createElement('strong');
            title.textContent = 'Nenhuma conversa ainda';
            const subtitle = document.createElement('span');
            subtitle.textContent = 'Suas conversas aparecerão aqui.';
            empty.appendChild(title);
            empty.appendChild(subtitle);
            chatListEl.appendChild(empty);
            return;
        }

        for (const chat of chats) {
            const item = document.createElement('div');
            item.className = 'chat-item' + (chat.id === chatId ? ' active' : '');
            item.dataset.id = chat.id;

            const chatIcon = document.createElement('div');
            chatIcon.className = 'chat-icon';
            chatIcon.appendChild(icon('message'));

            const info = document.createElement('div');
            info.className = 'chat-info';

            const title = document.createElement('div');
            title.className = 'chat-title';
            title.textContent = chat.title || ('Conversa #' + chat.id);

            const meta = document.createElement('div');
            meta.className = 'chat-meta';
            meta.appendChild(icon('clock'));
            const date = document.createElement('span');
            date.textContent = formatDate(chat.createdAt);
            meta.appendChild(date);

            info.appendChild(title);
            info.appendChild(meta);

            const del = document.createElement('button');
            del.className = 'chat-delete';
            del.type = 'button';
            del.title = 'Apagar conversa';
            del.setAttribute('aria-label', 'Apagar conversa: ' + (chat.title || ('Conversa #' + chat.id)));
            del.appendChild(icon('trash'));
            del.addEventListener('click', async function (e) {
                e.stopPropagation();
                await deleteChat(chat.id);
            });

            item.appendChild(chatIcon);
            item.appendChild(info);
            item.appendChild(del);
            item.addEventListener('click', function () {
                openChat(chat.id);
            });

            chatListEl.appendChild(item);
        }
    }

    async function openChat(id) {
        chatId = id;
        clearMessages();
        renderChatListActive(id);
        closeSidebar();
        try {
            const res = await fetch('/api/chat/' + id);
            if (!res.ok) {
                addMessage('Erro ao carregar o histórico.', 'error');
                return;
            }
            const history = await res.json();
            if (!history || history.length === 0) {
                showEmpty();
                return;
            }
            for (const msg of history) {
                addMessage(msg.content, msg.role === 'USER' ? 'user' : 'bot', msg.createdAt);
            }
        } catch (err) {
            addMessage('Falha de conexão com o servidor.', 'error');
        }
        inputEl.focus();
    }

    function renderChatListActive(id) {
        const items = chatListEl.querySelectorAll('.chat-item');
        for (const it of items) {
            it.classList.toggle('active', Number(it.dataset.id) === id);
        }
    }

    async function deleteChat(id) {
        try {
            const res = await fetch('/api/chat/' + id, { method: 'DELETE' });
            if (!res.ok && res.status !== 204) return;
            if (id === chatId) {
                chatId = null;
                showEmpty();
            }
            await loadChats();
        } catch (err) {
            // ignore
        }
    }

    function resetToNewChat() {
        chatId = null;
        showEmpty();
        renderChatListActive(null);
        closeSidebar();
        resizeInput();
        inputEl.focus();
    }

    async function sendMessage() {
        const message = inputEl.value.trim();
        if (!message) return;

        clearMessages();
        addMessage(message, 'user');
        inputEl.value = '';
        resizeInput();
        sendBtn.disabled = true;
        showTyping(true);

        try {
            const res = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ chatId: chatId, message: message })
            });

            const data = await res.json();
            if (!res.ok) {
                addMessage('Erro: ' + (data.message || 'Falha na requisição.'), 'error');
                return;
            }

            const wasNewChat = chatId === null;
            chatId = data.chatId;
            addMessage(data.reply, 'bot');
            if (wasNewChat) {
                await loadChats();
                renderChatListActive(chatId);
            }
        } catch (err) {
            addMessage('Falha de conexão com o servidor. Tente novamente.', 'error');
        } finally {
            showTyping(false);
            sendBtn.disabled = false;
            inputEl.focus();
        }
    }

    function resizeInput() {
        inputEl.style.height = 'auto';
        inputEl.style.height = Math.min(inputEl.scrollHeight, 160) + 'px';
    }

    function openSidebar() {
        sidebar.classList.add('open');
        overlay.classList.add('show');
    }

    function closeSidebar() {
        sidebar.classList.remove('open');
        overlay.classList.remove('show');
    }

    document.getElementById('newChatBtn').addEventListener('click', resetToNewChat);
    document.getElementById('menuBtn').addEventListener('click', openSidebar);
    document.getElementById('closeSidebarBtn').addEventListener('click', closeSidebar);
    overlay.addEventListener('click', closeSidebar);

    composerForm.addEventListener('submit', function (e) {
        e.preventDefault();
        sendMessage();
    });

    inputEl.addEventListener('input', resizeInput);

    inputEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    messagesEl.addEventListener('click', function (e) {
        const suggestion = e.target.closest('.suggestion');
        if (!suggestion) return;
        inputEl.value = suggestion.dataset.message || '';
        resizeInput();
        inputEl.focus();
        sendMessage();
    });

    document.addEventListener('keydown', function (e) {
        if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
            e.preventDefault();
            resetToNewChat();
        } else if (e.key === 'Escape') {
            closeSidebar();
        }
    });

    showEmpty();
    loadChats();
})();
