PR.registerLangHandler(PR.createSimpleLexer([[PR.PR_PLAIN,/^[\t \xA0a-gi-z0-9]+/,null,'	 \xa0abcdefgijklmnopqrstuvwxyz0123456789'],[PR.PR_PUNCTUATION,/^[=*~\^\[\]]+/,null,'=*~^[]']],[['lang-wiki.meta',/(?:^^|\r\n?|\n)(#[a-z]+)\b/],[PR.PR_LITERAL,/^(?:[A-Z][a-z][a-z0-9]+[A-Z][a-z][a-zA-Z0-9]+)\b/],['lang-',/^\{\{\{([\s\S]+?)\}\}\}/],['lang-',/^`([^\r\n`]+)`/],[PR.PR_STRING,/^https?:\/\/[^\/?#\s]*(?:\/[^?#\s]*)?(?:\?[^#\s]*)?(?:#\S*)?/i],[PR.PR_PLAIN,/^(?:\r\n|[\s\S])[^#=*~^A-Zh\{`\[\r\n]*/]]),['wiki']),PR.registerLangHandler(PR.createSimpleLexer([[PR.PR_KEYWORD,/^#[a-z]+/i,null,'#']],[]),['wiki.meta'])