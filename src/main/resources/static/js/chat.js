(function () {
	const CHAT_ENDPOINT = "/api/chat";
	const DOCUMENTS_ENDPOINT = "/api/documents";
	const THEME_STORAGE_KEY = "aichatbot.theme";
	const CONVERSATIONS_STORAGE_KEY = "aichatbot.conversations";
	const ACTIVE_ID_STORAGE_KEY = "aichatbot.activeConversationId";
	const WELCOME_MESSAGE = "Hi! I'm Claude. Ask me anything.";
	const DEFAULT_TITLE = "New chat";
	const ALLOWED_DOCUMENT_EXTENSIONS = [".pdf", ".txt", ".md"];
	const MAX_DOCUMENT_SIZE_BYTES = 15 * 1024 * 1024;

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
	const attachBtn = document.getElementById("attach-btn");
	const fileInputEl = document.getElementById("file-input");
	const attachUrlBtn = document.getElementById("attach-url-btn");
	const documentBannerEl = document.getElementById("document-banner");
	const documentBannerTextEl = document.getElementById("document-banner-text");
	const documentRemoveBtn = document.getElementById("document-remove-btn");

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
		return {
			id: generateId(),
			title: DEFAULT_TITLE,
			messages: [],
			createdAt: now,
			updatedAt: now,
			documentId: null,
			documentName: null
		};
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

	function renderDocumentBanner() {
		const conv = getActiveConversation();
		if (conv && conv.documentId) {
			documentBannerTextEl.textContent = "📄 " + conv.documentName;
			documentBannerEl.classList.remove("hidden");
		} else {
			documentBannerEl.classList.add("hidden");
		}
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
		renderDocumentBanner();
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
		renderDocumentBanner();
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
		renderDocumentBanner();
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
					}),
					documentId: conv.documentId || null
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

	function confirmReplaceIfNeeded(newLabel) {
		const conv = getActiveConversation();
		if (conv.documentId) {
			return confirm("Replace the current attached document (\"" + conv.documentName + "\") with " + newLabel + "?");
		}
		return true;
	}

	function applyDocumentResult(data, sourceLabel) {
		const conv = getActiveConversation();
		conv.documentId = data.documentId;
		conv.documentName = data.filename;

		const now = Date.now();
		conv.messages.push({ role: "user", content: sourceLabel + data.filename, timestamp: now });
		conv.messages.push({ role: "assistant", content: data.summary, timestamp: now + 1 });
		if (conv.title === DEFAULT_TITLE) {
			conv.title = truncate(data.filename, 32);
		}
		conv.updatedAt = now;
		moveToFront(conv.id);
		saveState();

		renderMessages();
		renderSidebar();
		renderActiveTitle();
		renderDocumentBanner();
	}

	async function uploadDocument(file) {
		const lowerName = file.name.toLowerCase();
		const hasAllowedExtension = ALLOWED_DOCUMENT_EXTENSIONS.some(function (ext) {
			return lowerName.endsWith(ext);
		});
		if (!hasAllowedExtension) {
			alert("Please upload a PDF, .txt, or .md file.");
			return;
		}
		if (file.size > MAX_DOCUMENT_SIZE_BYTES) {
			alert("File is too large. Max size is 15MB.");
			return;
		}
		if (!confirmReplaceIfNeeded("\"" + file.name + "\"")) {
			return;
		}

		setTyping(true);
		setSending(true);

		try {
			const formData = new FormData();
			formData.append("file", file);

			const res = await fetch(DOCUMENTS_ENDPOINT, { method: "POST", body: formData });

			if (!res.ok) {
				const message = await res.text();
				throw new Error(message || ("Upload failed with status " + res.status));
			}

			const data = await res.json();
			applyDocumentResult(data, "📄 Uploaded document: ");
		} catch (err) {
			appendMessage("bot", "Sorry, I couldn't process that document. " + err.message, new Date());
			console.error(err);
		} finally {
			setTyping(false);
			setSending(false);
			inputEl.focus();
		}
	}

	async function uploadDocumentFromUrl(url) {
		if (!confirmReplaceIfNeeded("this URL")) {
			return;
		}

		setTyping(true);
		setSending(true);

		try {
			const res = await fetch(DOCUMENTS_ENDPOINT + "/url", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ url: url })
			});

			if (!res.ok) {
				const message = await res.text();
				throw new Error(message || ("Fetching that URL failed with status " + res.status));
			}

			const data = await res.json();
			applyDocumentResult(data, "🔗 Attached from URL: ");
		} catch (err) {
			appendMessage("bot", "Sorry, I couldn't process that URL. " + err.message, new Date());
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

	attachBtn.addEventListener("click", function () {
		fileInputEl.click();
	});

	fileInputEl.addEventListener("change", function () {
		const file = fileInputEl.files[0];
		fileInputEl.value = "";
		if (file) {
			uploadDocument(file);
		}
	});

	attachUrlBtn.addEventListener("click", function () {
		const url = prompt("Paste a URL to a PDF or webpage:");
		if (url && url.trim()) {
			uploadDocumentFromUrl(url.trim());
		}
	});

	documentRemoveBtn.addEventListener("click", function () {
		const conv = getActiveConversation();
		if (conv && conv.documentId && confirm("Remove the attached document from this chat? Prior messages stay in history.")) {
			conv.documentId = null;
			conv.documentName = null;
			saveState();
			renderDocumentBanner();
		}
	});

	initTheme();
	loadState();
	renderMessages();
	renderSidebar();
	renderActiveTitle();
	renderDocumentBanner();
	inputEl.focus();
})();
