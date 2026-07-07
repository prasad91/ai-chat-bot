(function () {
	const CHAT_ENDPOINT = "/api/chat";
	const THEME_STORAGE_KEY = "aichatbot.theme";
	const CONVERSATIONS_STORAGE_KEY = "aichatbot.conversations";
	const ACTIVE_ID_STORAGE_KEY = "aichatbot.activeConversationId";
	const WELCOME_MESSAGE = "Hi! I'm Claude. Ask me anything.";
	const DEFAULT_TITLE = "New chat";

	const messagesEl = document.getElementById("messages");
	const typingIndicatorEl = document.getElementById("typing-indicator");
	const formEl = document.getElementById("chat-form");
	const inputEl = document.getElementById("chat-input");
	const sendBtn = document.getElementById("send-btn");
	const themeToggleBtn = document.getElementById("theme-toggle");
	const newChatBtn = document.getElementById("new-chat-btn");
	const deleteChatBtn = document.getElementById("delete-chat-btn");
	const conversationListEl = document.getElementById("conversation-list");
	const chatTitleEl = document.getElementById("chat-title");
	const sidebarEl = document.getElementById("sidebar");
	const sidebarToggleBtn = document.getElementById("sidebar-toggle");
	const sidebarBackdropEl = document.getElementById("sidebar-backdrop");

	let conversations = [];
	let activeId = null;

	function generateId() {
		if (window.crypto && crypto.randomUUID) {
			return crypto.randomUUID();
		}
		return "conv-" + Date.now() + "-" + Math.random().toString(16).slice(2);
	}

	function createConversation() {
		const now = Date.now();
		return { id: generateId(), title: DEFAULT_TITLE, messages: [], createdAt: now, updatedAt: now };
	}

	function loadState() {
		try {
			const stored = JSON.parse(localStorage.getItem(CONVERSATIONS_STORAGE_KEY) || "[]");
			if (Array.isArray(stored) && stored.length > 0) {
				conversations = stored;
			}
		} catch (err) {
			console.error("Failed to parse stored conversations", err);
		}

		if (conversations.length === 0) {
			conversations = [createConversation()];
		}

		const storedActiveId = localStorage.getItem(ACTIVE_ID_STORAGE_KEY);
		activeId = conversations.some(function (c) { return c.id === storedActiveId; })
			? storedActiveId
			: conversations[0].id;
	}

	function saveState() {
		localStorage.setItem(CONVERSATIONS_STORAGE_KEY, JSON.stringify(conversations));
		localStorage.setItem(ACTIVE_ID_STORAGE_KEY, activeId);
	}

	function getActiveConversation() {
		return conversations.find(function (c) { return c.id === activeId; });
	}

	function moveToFront(id) {
		const index = conversations.findIndex(function (c) { return c.id === id; });
		if (index > 0) {
			const [conv] = conversations.splice(index, 1);
			conversations.unshift(conv);
		}
	}

	function truncate(text, max) {
		const trimmed = text.trim();
		return trimmed.length > max ? trimmed.slice(0, max).trim() + "..." : trimmed;
	}

	function formatTime(date) {
		return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
	}

	function scrollToBottom() {
		messagesEl.scrollTop = messagesEl.scrollHeight;
	}

	function appendMessage(role, text, date) {
		const row = document.createElement("div");
		row.className = "message-row " + role;

		const bubble = document.createElement("div");
		bubble.className = "message-bubble";
		bubble.textContent = text;

		const meta = document.createElement("div");
		meta.className = "message-meta";

		const time = document.createElement("span");
		time.textContent = formatTime(date);

		const copyBtn = document.createElement("button");
		copyBtn.type = "button";
		copyBtn.className = "copy-btn";
		copyBtn.textContent = "Copy";
		copyBtn.addEventListener("click", function () {
			navigator.clipboard.writeText(text).then(function () {
				copyBtn.textContent = "Copied!";
				setTimeout(function () {
					copyBtn.textContent = "Copy";
				}, 1200);
			});
		});

		meta.appendChild(time);
		meta.appendChild(copyBtn);

		row.appendChild(bubble);
		row.appendChild(meta);
		messagesEl.appendChild(row);
		scrollToBottom();
	}

	function renderMessages() {
		messagesEl.innerHTML = "";
		const conv = getActiveConversation();
		if (!conv || conv.messages.length === 0) {
			appendMessage("bot", WELCOME_MESSAGE, new Date());
			return;
		}
		conv.messages.forEach(function (m) {
			appendMessage(m.role === "assistant" ? "bot" : "user", m.content, new Date(m.timestamp));
		});
	}

	function renderSidebar() {
		conversationListEl.innerHTML = "";
		conversations.forEach(function (conv) {
			const item = document.createElement("button");
			item.type = "button";
			item.className = "conversation-item" + (conv.id === activeId ? " active" : "");
			item.dataset.id = conv.id;

			const title = document.createElement("span");
			title.className = "conversation-item-title";
			title.textContent = conv.title;

			const deleteBtn = document.createElement("button");
			deleteBtn.type = "button";
			deleteBtn.className = "conversation-item-delete";
			deleteBtn.textContent = "✕";
			deleteBtn.title = "Delete chat";
			deleteBtn.addEventListener("click", function (e) {
				e.stopPropagation();
				deleteConversation(conv.id);
			});

			item.appendChild(title);
			item.appendChild(deleteBtn);
			item.addEventListener("click", function () {
				switchConversation(conv.id);
			});

			conversationListEl.appendChild(item);
		});
	}

	function renderActiveTitle() {
		const conv = getActiveConversation();
		chatTitleEl.textContent = conv ? conv.title : "AI Chat Bot";
	}

	function switchConversation(id) {
		if (id === activeId) {
			closeSidebarOnMobile();
			return;
		}
		activeId = id;
		saveState();
		renderMessages();
		renderSidebar();
		renderActiveTitle();
		closeSidebarOnMobile();
		inputEl.focus();
	}

	function newChat() {
		const conv = createConversation();
		conversations.unshift(conv);
		activeId = conv.id;
		saveState();
		renderMessages();
		renderSidebar();
		renderActiveTitle();
		closeSidebarOnMobile();
		inputEl.focus();
	}

	function deleteConversation(id) {
		if (!confirm("Delete this chat?")) {
			return;
		}
		conversations = conversations.filter(function (c) { return c.id !== id; });
		if (conversations.length === 0) {
			conversations = [createConversation()];
		}
		if (activeId === id) {
			activeId = conversations[0].id;
		}
		saveState();
		renderMessages();
		renderSidebar();
		renderActiveTitle();
	}

	function setTyping(visible) {
		typingIndicatorEl.classList.toggle("hidden", !visible);
		if (visible) {
			scrollToBottom();
		}
	}

	function setSending(isSending) {
		sendBtn.disabled = isSending;
		inputEl.disabled = isSending;
	}

	async function sendMessage(text) {
		const conv = getActiveConversation();
		const now = Date.now();

		conv.messages.push({ role: "user", content: text, timestamp: now });
		if (conv.title === DEFAULT_TITLE) {
			conv.title = truncate(text, 32);
		}
		conv.updatedAt = now;
		moveToFront(conv.id);
		saveState();
		renderSidebar();
		renderActiveTitle();

		appendMessage("user", text, new Date(now));
		setTyping(true);
		setSending(true);

		try {
			const res = await fetch(CHAT_ENDPOINT, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					messages: conv.messages.map(function (m) {
						return { role: m.role, content: m.content };
					})
				})
			});

			if (!res.ok) {
				throw new Error("Request failed with status " + res.status);
			}

			const data = await res.json();
			const replyTime = Date.now();
			conv.messages.push({ role: "assistant", content: data.reply, timestamp: replyTime });
			conv.updatedAt = replyTime;
			saveState();
			appendMessage("bot", data.reply, new Date(replyTime));
		} catch (err) {
			appendMessage("bot", "Sorry, something went wrong reaching the server.", new Date());
			console.error(err);
		} finally {
			setTyping(false);
			setSending(false);
			inputEl.focus();
		}
	}

	function applyTheme(theme) {
		document.documentElement.setAttribute("data-theme", theme);
		themeToggleBtn.textContent = theme === "dark" ? "☀️" : "🌙";
		localStorage.setItem(THEME_STORAGE_KEY, theme);
	}

	function initTheme() {
		const stored = localStorage.getItem(THEME_STORAGE_KEY);
		if (stored) {
			applyTheme(stored);
			return;
		}
		const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
		applyTheme(prefersDark ? "dark" : "light");
	}

	function autoResizeInput() {
		inputEl.style.height = "auto";
		inputEl.style.height = Math.min(inputEl.scrollHeight, 140) + "px";
	}

	function openSidebar() {
		sidebarEl.classList.add("open");
		sidebarBackdropEl.classList.remove("hidden");
	}

	function closeSidebar() {
		sidebarEl.classList.remove("open");
		sidebarBackdropEl.classList.add("hidden");
	}

	function closeSidebarOnMobile() {
		if (window.matchMedia("(max-width: 768px)").matches) {
			closeSidebar();
		}
	}

	formEl.addEventListener("submit", function (e) {
		e.preventDefault();
		const text = inputEl.value.trim();
		if (!text) {
			return;
		}
		inputEl.value = "";
		autoResizeInput();
		sendMessage(text);
	});

	inputEl.addEventListener("keydown", function (e) {
		if (e.key === "Enter" && !e.shiftKey) {
			e.preventDefault();
			formEl.requestSubmit();
		}
	});

	inputEl.addEventListener("input", autoResizeInput);

	newChatBtn.addEventListener("click", newChat);

	deleteChatBtn.addEventListener("click", function () {
		if (activeId) {
			deleteConversation(activeId);
		}
	});

	themeToggleBtn.addEventListener("click", function () {
		const current = document.documentElement.getAttribute("data-theme");
		applyTheme(current === "dark" ? "light" : "dark");
	});

	sidebarToggleBtn.addEventListener("click", function () {
		if (sidebarEl.classList.contains("open")) {
			closeSidebar();
		} else {
			openSidebar();
		}
	});

	sidebarBackdropEl.addEventListener("click", closeSidebar);

	initTheme();
	loadState();
	renderMessages();
	renderSidebar();
	renderActiveTitle();
	inputEl.focus();
})();
