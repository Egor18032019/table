package org.tablebuilder.demo.service;

import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tablebuilder.demo.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.ai.vectorstore.SearchRequest;

@Slf4j
@Service
@AllArgsConstructor
public class SimpleAiAssistantService {
    @Autowired
    private final ChatClient chatClient;
    @Autowired
    private final MetadataService metadataService;
    @Autowired
    private VectorStore vectorStore;

    /**
     * Основной метод обработки естественно-языковых запросов пользователя.
     * Координирует весь процесс поиска: анализ запроса, поиск данных и формирование ответа.
     *
     * @param request объект запроса, содержащий текст запроса и информацию о пользователе
     * @return ответ с найденными источниками данных или сообщением об ошибке
     */
    public AiSearchResponse processNaturalLanguageQuery(AiSearchRequest request) {
        try {
            log.info("Processing user query: {}", request.getQuery());

            // 1. Анализ запроса с помощью ИИ для извлечения ключевых слов
            QueryAnalysis analysis = analyzeQueryWithAI(request.getQuery());

            // 2. Поиск релевантных файлов и листов по метаданным
            List<AiSearchResponse.DataSource> metadataResults = searchMetadata(analysis);

            // 3. Поиск релевантных фрагментов ПО СОДЕРЖИМУ с помощью VectorStore (RAG)
            List<AiSearchResponse.DataSource> contentResults = searchContent(analysis);

            // 4. Объединение и сортировка результатов из обоих источников
            List<AiSearchResponse.DataSource> combinedResults = combineAndSortResults(metadataResults, contentResults);
            // 3. Формирование понятного пользователю ответа
            return buildAIResponse(combinedResults, request.getQuery(), analysis);

        } catch (Exception e) {
            log.error("AI search error for query '{}': {}", request.getQuery(), e.getMessage(), e);
            return createErrorResponse("Ошибка обработки запроса. Попробуйте переформулировать вопрос.");
        }
    }

    /**
     * Анализирует текстовый запрос пользователя с помощью ИИ модели Gemma3.
     * Извлекает ключевые слова и определяет намерение пользователя.
     *
     * @param query исходный текстовый запрос пользователя на русском языке
     * @return анализ запроса с извлеченными ключевыми словами и метаданными
     */
    private QueryAnalysis analyzeQueryWithAI(String query) {
        try {
            String prompt = String.format("""
                        Ты - AI ассистент для поиска данных в системе. Пользователь ищет: "%s"
                             \s
                              Проанализируй запрос и выдели ключевые слова для поиска в Excel файлах.
                              Учитывай:
                              - Русскоязычные термины и их синонимы
                              - Названия отделов, типы данных (продажи, сотрудники, финансы)
                              - Возможные варианты написания
                             \s
                              ВЕРНИ ТОЛЬКО JSON без пояснений:
                              {
                                  "keywords": ["keyword1", "keyword2"],
                                  "intent": "поиск_файлов"
                              }
                             \s
                              Пример для "Где найти отчеты по продажам за март?":
                              {"keywords": ["продажи", "отчеты", "март"], "intent": "поиск_файлов"}
                    """, query);

            String response = chatClient.prompt()
                    .user(prompt)
                    .options(OllamaOptions.builder()
                            .model("gemma3:4b-it-q4_K_M")
                            .temperature(0.1)
                            .topP(0.9)
                            .build())
                    .call()
                    .content();

            return parseAIResponse(response);

        } catch (Exception e) {
            log.warn("AI analysis failed for query '{}', using fallback keyword extraction: {}",
                    query, e.getMessage());
            return createFallbackAnalysis(query);
        }
    }

    /**
     * Парсит JSON-ответ от ИИ модели и преобразует его в объект QueryAnalysis.
     * Обрабатывает различные форматы ответов и обеспечивает отказоустойчивость.
     *
     * @param response сырой текстовый ответ от ИИ модели
     * @return структурированный анализ запроса
     */
    private QueryAnalysis parseAIResponse(String response) {
        QueryAnalysis analysis = new QueryAnalysis();

        try {
            log.debug("Parsing AI response: {}", response);

            // Поиск секции с ключевыми словами в ответе
            if (response.contains("keywords") && response.contains("[")) {
                int startIdx = response.indexOf("[") + 1;
                int endIdx = response.indexOf("]", startIdx);

                if (startIdx > 0 && endIdx > startIdx) {
                    String keywordsStr = response.substring(startIdx, endIdx);
                    analysis.setSearchKeywords(Arrays.stream(keywordsStr.split(","))
                            .map(String::trim)
                            .map(s -> s.replace("\"", "").replace("'", ""))
                            .filter(word -> word.length() > 2) // Фильтруем слишком короткие слова
                            .collect(Collectors.toList()));

                    log.info("Extracted keywords from AI: {}", analysis.getSearchKeywords());
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing AI response, will use fallback: {}", e.getMessage());
        }

        // Резервный вариант если парсинг не удался
        if (analysis.getSearchKeywords() == null || analysis.getSearchKeywords().isEmpty()) {
            analysis.setSearchKeywords(extractKeywordsBasic(response));
            log.info("Using fallback keywords: {}", analysis.getSearchKeywords());
        }

        return analysis;
    }

    /**
     * Выполняет поиск по метаданным всех файлов и листов используя ключевые слова из анализа.
     * Рассчитывает релевантность каждого источника данных на основе совпадений в названиях.
     *
     * @param analysis результат анализа запроса с ключевыми словами
     * @return отсортированный список релевантных источников данных
     */
    private List<AiSearchResponse.DataSource> searchMetadata(QueryAnalysis analysis) {
        List<AiSearchResponse.DataSource> results = new ArrayList<>();
        List<FileInfo> allFiles = metadataService.getAllFiles();

        log.info("Searching through {} files with keywords: {}",
                allFiles.size(), analysis.getSearchKeywords());

        for (FileInfo file : allFiles) {
            // Расчет релевантности файла по названию
            int fileScore = calculateRelevanceScore(file.getDisplayName(), analysis.getSearchKeywords());



                try {
                    // Получаем все листы файла для детального поиска
                    List<SheetInfo> sheets = metadataService.getSheetsByFile(file.getFileName());

                    for (SheetInfo sheet : sheets) {
                        // Расчет релевантности листа по названию
                        int sheetScore = calculateRelevanceScore(sheet.getOriginalSheetName(), analysis.getSearchKeywords());

                        // Дополнительная проверка релевантности по названиям колонок
                        List<String> columns = sheet.getColumns();
                        int columnScore = columns != null ?
                                calculateRelevanceScore(String.join(" ", columns), analysis.getSearchKeywords()) : 0;

                        // Общая релевантность
                        int totalScore = (fileScore + sheetScore + columnScore)/3;

                        // Добавляем в результаты если превышен порог релевантности
                        if (totalScore > 8) {
                            AiSearchResponse.DataSource source = createDataSource(file, sheet, totalScore);
                            results.add(source);
                            log.debug("Added source: {} with score: {}", source.getFileName(), totalScore);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error processing sheets for file '{}': {}", file.getFileName(), e.getMessage());
                }

        }

        // Сортировка по убыванию релевантности
        results.sort((a, b) -> Integer.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        log.info("Search completed. Found {} relevant sources", results.size());

        return results;
    }

    /**
     * Выполняет поиск по содержимому листов, используя VectorStore.
     *
     * @param analysis Результат анализа запроса с ключевыми словами.
     * @return Список релевантных источников данных, извлеченных из содержимого.
     */
    private List<AiSearchResponse.DataSource> searchContent(QueryAnalysis analysis) {
        List<AiSearchResponse.DataSource> results = new ArrayList<>();
        if (analysis.getSearchKeywords() == null || analysis.getSearchKeywords().isEmpty()) {
            log.debug("No keywords provided for content search.");
            return results;
        }

        String queryForVectorStore = String.join(" ", analysis.getSearchKeywords());
        log.debug("Searching content in VectorStore for query: '{}'", queryForVectorStore);

        try {
            // Выполняем поиск в VectorStore
            // SearchRequest.defaults().withTopK(5) - настраиваем количество результатов
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder().query(queryForVectorStore)
                            .similarityThreshold(0.8d).
                            topK(3).build()
            );

            for (Document doc : documents) {
                Map<String, Object> metadata = doc.getMetadata();
                String fileName = (String) metadata.get("file_name");
                String sheetName = (String) metadata.get("sheet_name");
                String tableName = (String) metadata.get("table_name"); // Имя таблицы в БД
                Integer rowIndex = (Integer) metadata.get("row_index");

                // Получаем информацию о файле и листе из MetadataService для полноты
                FileInfo fileInfo = metadataService.getFileInfoByDisplayName(fileName);
                if (fileInfo != null) {
                    // Определяем релевантность на основе схожести (score) или содержимого документа
                    // Score доступен через metadata.get("score") если векторное хранилище его возвращает
                    Double score = (Double) metadata.get("score"); // Может быть null, зависит от реализации
                    int relevanceScore = score != null ? (int) (score * 100) : 50; // Пример: преобразуем score в 0-100, или используем дефолт

                    // Создаем DataSource из найденного документа
                    AiSearchResponse.DataSource source = new AiSearchResponse.DataSource();
                    source.setFileName(fileName);
                    source.setSheetName(sheetName);
                    source.setRelevanceScore(relevanceScore); // Используем score как релевантность
                    // Описание может быть сгенерировано из содержимого документа или просто указать, что найдено по содержимому
                    source.setDescription("Найдено в содержимом: " + Objects.requireNonNull(doc.getText()).substring(0, Math.min(100, doc.getText().length())) + tableName + " в " + sheetName);
                    results.add(source);
                    log.debug("Added content source: {} - {} with relevance score: {}", fileName, sheetName, relevanceScore);
                }
            }
        } catch (Exception e) {
            log.error("Error searching content in VectorStore: {}", e.getMessage(), e);
            // Не прерываем общий процесс, возвращаем пустой список
        }

        log.info("Content search completed. Found {} relevant sources", results.size());
        return results;
    }
    /**
     * Объединяет результаты поиска по метаданным и по содержимому, устраняет дубликаты и сортирует по релевантности.
     * @param metadataResults Результаты поиска по метаданным.
     * @param contentResults Результаты поиска по содержимому.
     * @return Объединенный и отсортированный список источников данных.
     */
    private List<AiSearchResponse.DataSource> combineAndSortResults(List<AiSearchResponse.DataSource> metadataResults, List<AiSearchResponse.DataSource> contentResults) {
        // Используем Set для устранения дубликатов, основываясь на fileName и sheetName
        Set<AiSearchResponse.DataSource> uniqueResults = new HashSet<>(metadataResults);
        uniqueResults.addAll(contentResults);

        // Преобразуем обратно в список и сортируем
        List<AiSearchResponse.DataSource> combinedList = new ArrayList<>(uniqueResults);
        combinedList.sort((a, b) -> Integer.compare(b.getRelevanceScore(), a.getRelevanceScore()));

        log.info("Combined and sorted results: {} metadata, {} content, total unique: {}", metadataResults.size(), contentResults.size(), combinedList.size());
        return combinedList;
    }


    /**
     * Создает объект DataSource на основе файла и листа с расчетом описания.
     *
     * @param file           информация о файле
     * @param sheet          информация о листе
     * @param relevanceScore рассчитанная релевантность (0-100)
     * @return готовый объект источника данных
     */
    private AiSearchResponse.DataSource createDataSource(FileInfo file, SheetInfo sheet, int relevanceScore) {
        AiSearchResponse.DataSource source = new AiSearchResponse.DataSource();
        source.setFileName(file.getDisplayName());
        source.setSheetName(sheet.getOriginalSheetName());
        source.setRelevanceScore(relevanceScore);
        source.setDescription(String.format(
                "Содержит %d строк данных в %d колонках",
                sheet.getRowCount(), sheet.getColumnCount()
        ));
        return source;
    }

    /**
     * Рассчитывает релевантность текста на основе ключевых слов.
     * Учитывает множественные совпадения и возвращает оценку от 0 до 100.
     *
     * @param text     анализируемый текст (название файла, листа или колонок)
     * @param keywords список ключевых слов для поиска
     * @return оценка релевантности (0 - нет совпадений, 100 - полное совпадение)
     */
    private int calculateRelevanceScore(String text, List<String> keywords) {
        if (text == null || keywords == null || keywords.isEmpty()) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        int score = 0;

        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();

            // Полное совпадение слова
            if (lowerText.contains(lowerKeyword)) {
                score += 25;
            }
            // Частичное совпадение (для длинных ключевых слов)
            else if (lowerKeyword.length() > 3) {
                // Проверяем вхождение частей ключевого слова
                for (int i = 0; i <= lowerKeyword.length() - 3; i++) {
                    String part = lowerKeyword.substring(i, Math.min(i + 4, lowerKeyword.length()));
                    if (lowerText.contains(part)) {
                        score += 10;
                        break;
                    }
                }
            }
        }

        return Math.min(score, 100);
    }

    /**
     * Формирует финальный ответ пользователю в удобочитаемом формате.
     * Включает список найденных источников с описаниями и рекомендациями.
     *
     * @param results  список найденных релевантных источников
     * @param query    оригинальный запрос пользователя
     * @param analysis результат анализа запроса
     * @return структурированный ответ для фронтенда
     */
    private AiSearchResponse buildAIResponse(List<AiSearchResponse.DataSource> results,
                                             String query,
                                             QueryAnalysis analysis) {
        StringBuilder answer = new StringBuilder();

        if (!results.isEmpty()) {
            answer.append("🤖 **Результаты поиска по запросу:** \"").append(query).append("\"\n\n");

            // Ограничиваем количество результатов для удобства восприятия
            int maxResults = Math.min(results.size(), 5);

            for (int i = 0; i < maxResults; i++) {
                AiSearchResponse.DataSource source = results.get(i);
                answer.append("📊 ").append(i + 1).append(". **").append(source.getFileName()).append("**\n");
                answer.append("   📑 Лист: ").append(source.getSheetName()).append("\n");
                answer.append("   ✅ Релевантность: ").append(source.getRelevanceScore()).append("%\n");
                answer.append("   📈 ").append(source.getDescription()).append("\n\n");
            }

            // Добавляем подсказку если результатов много
            if (results.size() > maxResults) {
                answer.append("💡 *И еще ").append(results.size() - maxResults)
                        .append(" источников...*\n\n");
            }

            answer.append("🎯 *Для просмотра данных откройте соответствующий файл в системе*");

        } else {
            answer.append("❌ По запросу \"").append(query).append("\" не найдено подходящих данных.\n\n");
            answer.append("**Возможные причины и рекомендации:**\n");
            answer.append("• Проверьте список доступных файлов в системе\n");
            answer.append("• Используйте более конкретные формулировки запроса\n");
            answer.append("• Попробуйте синонимы или родственные термины\n");
            answer.append("• Убедитесь, что нужные данные были загружены в систему\n");
        }

        AiSearchResponse response = new AiSearchResponse();
        response.setSuccess(true);
        response.setAnswer(answer.toString());
        response.setDataSources(results);

        log.info("Response built successfully. Found {} sources for query '{}'",
                results.size(), query);

        return response;
    }

    /**
     * Базовое извлечение ключевых слов из текста запроса.
     * Используется как fallback при недоступности ИИ анализа.
     * Фильтрует стоп-слова и слишком короткие слова.
     *
     * @param text исходный текст для анализа
     * @return список релевантных ключевых слов
     */
    /**
     * Базовое извлечение ключевых слов из текста запроса.
     * Используется как fallback при недоступности ИИ анализа.
     * Фильтрует стоп-слова, слишком короткие слова и потенциально некорректные символы.
     *
     * @param text исходный текст для анализа
     * @return список релевантных ключевых слов
     */
    private List<String> extractKeywordsBasic(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        // Список русских стоп-слов для фильтрации
        String[] stopWords = {"где", "найти", "данные", "информация", "о", "об", "в", "и", "или",
                "для", "как", "что", "где", "когда", "по", "с", "со", "у", "не",
                "no", "на", "за", "от", "до", "из", "же", "бы", "ли", "то", "это"};

        // Убираем replacement character и другие потенциально некорректные символы
        // Оставляем только буквы, цифры, пробелы и базовые знаки препинания
        String cleanedText = text.replaceAll("[^\\p{L}\\p{N}\\s\\-_,.!?;:]", "");

        log.debug("Cleaned text for basic keyword extraction: '{}'", cleanedText);

        return Arrays.stream(cleanedText.toLowerCase().split("\\s+")) // Разбиваем по пробелам
                .map(String::trim) // Убираем лишние пробелы
                .filter(word -> word.length() > 2) // Фильтруем короткие слова
                .filter(word -> !Arrays.asList(stopWords).contains(word)) // Фильтруем стоп-слова
                .filter(word -> !word.isEmpty()) // Убедимся, что слово не пустое после очистки
                .distinct() // Удаляем дубликаты
                .collect(Collectors.toList());
    }

    /**
     * Создает резервный анализ запроса при недоступности ИИ.
     * Использует базовое извлечение ключевых слов.
     *
     * @param query оригинальный запрос пользователя
     * @return базовый анализ с извлеченными ключевыми словами
     */
    private QueryAnalysis createFallbackAnalysis(String query) {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setSearchKeywords(extractKeywordsBasic(query));
        log.info("Created fallback analysis with {} keywords", analysis.getSearchKeywords().size());
        return analysis;
    }

    /**
     * Создает стандартизированный ответ об ошибке.
     * Используется при возникновении исключений в основном потоке обработки.
     *
     * @param errorMessage детальное сообщение об ошибке для логирования
     * @return пользовательский ответ с сообщением об ошибке
     */
    private AiSearchResponse createErrorResponse(String errorMessage) {
        AiSearchResponse response = new AiSearchResponse();
        response.setSuccess(false);
        response.setError(errorMessage);
        response.setAnswer("""
                ⚠️ Извините, произошла техническая ошибка при обработке вашего запроса.
                
                Пожалуйста, попробуйте:
                • Переформулировать запрос
                • Проверить подключение к интернету  
                • Повторить попытку через несколько минут
                
                Если проблема сохраняется, обратитесь к администратору системы.
                """);
        return response;
    }


}