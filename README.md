# 基于SpringAI+PgVector+Ollama构建的本地RAG知识库实战指南




## 简介
最近在学习Rag遇到了大量坑，翻阅了大量资料后总结了一个SpringAI + PgVector的一个比较简单的实战例子。


## 技术栈详情

| 组件 | 版本/模型 | 用途 |
|------|-----------|------|
| **模型运行环境** | Ollama | 本地LLM推理服务 |
| **文本嵌入模型** | mxbai-embed-large | 将文本转换为高维向量 |
| **对话生成模型** | deepseek-r1:7b | 基于检索内容生成回答 |
| **向量数据库** | PgVector (PostgreSQL) | 存储和检索文档向量 |
| **应用框架** | SpringBoot 3.4.4 | 后端服务框架 |
| **Java版本** | Corretto-21 | 运行时环境 |

## 环境搭建

### 1. Ollama安装与配置

#### 安装Ollama
推荐参考官方文档：[Ollama安装指南](https://www.runoob.com/ollama/ollama-install.html)

#### 下载必需模型
```bash
# 下载对话生成模型（7B参数版本，推理速度快）
ollama pull deepseek-r1:7b

# 下载文本嵌入模型（高质量向量化）
ollama pull mxbai-embed-large:latest
```

> **说明**：模型下载完成后，Ollama会自动管理模型的加载和推理服务。

### 2. PgVector向量数据库部署

#### 创建数据存储目录
```bash
# 创建专用数据目录
sudo mkdir -p /opt/pgvector_data
sudo chown 999:999 /opt/pgvector_data
```

#### 启动PgVector容器
```bash
docker run --name pgvector \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD='postgres' \
  -e POSTGRES_DB=ragdemo \
  -p 5432:5432 \
  -v /opt/pgvector_data:/var/lib/postgresql/data \
  -d pgvector/pgvector:pg16
```

> **提示**：容器启动后，PgVector会自动安装向量扩展，支持高效的向量相似度计算。

## 代码实现

### 1. 核心依赖配置

在`pom.xml`中添加以下关键依赖：

```xml
<!-- Spring AI Ollama 集成，提供LLM调用能力 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>

<!-- Spring AI PgVector 向量存储，实现文档向量化存储 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
    <version>1.0.0-M7</version>
</dependency>

<!-- Spring AI Markdown 文档解析器 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-markdown-document-reader</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

### 2. 应用配置

`application.yml`完整配置：

```yaml
spring:
  application:
    name: RagDemo
  datasource:
    url: jdbc:postgresql://localhost:5432/ragdemo
    username: postgres
    password: postgres
    
  ai:
    vectorstore:
      pgvector:
        dimensions: 1024              # mxbai-embed-large模型的向量维度
        distance-type: COSINE_DISTANCE   # 余弦相似度计算方式
        max-document-batch-size: 10000   # 批量处理文档数量上限
        initialize-schema: true          # 自动创建vector_store表
        
    ollama:
      base-url: http://localhost:11434
      chat:
        model: deepseek-r1:7b
      embedding:
        model: mxbai-embed-large     # 指定嵌入模型

server:
  port: 8080
  servlet:
    context-path: /api
    
# API文档配置
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  api-docs:
    path: /v3/api-docs
  group-configs:
    - group: 'default'
      paths-to-match: '/**'
      packages-to-scan: com.swx.ragdemo.controller

# Knife4j增强配置
knife4j:
  enable: true
  setting:
    language: zh_cn
```

> **重要提示**：`initialize-schema: true`确保系统自动创建向量存储表，避免手动建表的繁琐操作。

### 3. 文档加载器实现

使用SpringAI提供的spring-ai-markdown-document-reader来对md文档进行读取和切片。

```java
@Slf4j
@Service
public class MyAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public MyAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 处理上传的文件，将其转换为Document列表
     * @param file 上传的Markdown文件
     * @return 文档片段列表
     */
    public List<Document> processUploadedFile(MultipartFile file) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        return processResource(resource);
    }

    /**
     * 核心文档处理逻辑
     * @param resource 文档资源
     * @return 按章节分割的文档片段
     */
    private List<Document> processResource(Resource resource) throws IOException {
        String fileName = resource.getFilename();
        
        // 配置Markdown解析策略
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)  // 使用水平分割线（---）分割文档
                .withIncludeCodeBlock(false)             // 排除代码块，避免干扰语义理解
                .withIncludeBlockquote(false)            // 排除引用块
                .withAdditionalMetadata("filename", fileName)  // 保留文件名元数据
                .build();
                
        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
        List<Document> documents = reader.get();
        
        log.info("成功解析文件 {} 为 {} 个文档片段", fileName, documents.size());
        return documents;
    }
}
```
processResource()方法会将一个md以标题分割为多个切片

#### 文档分割效果示例

对于以下Markdown内容：
```markdown
### 1. 物品持有与展示

- 所有生物实体都可以持有物品
- 当生物展示所持有物品时，所有玩家都可以看到该物品
- **物品展示框**也可用于展示单个物品
- 物品实体外观与其代表的物品保持一致

### 2. 物品行为

- 多数物品定义了在**玩家持有**与**生物持有**时的行为
- 某些物品在使用时可以在世界中放置方块或实体：
    - 如：`船（Boat）`使用后变为实体
    - 如：`床（Bed）`与`门（Door）`使用后变为多个方块
- 当物品被选中（位于快捷栏），其名称会短暂地显示在HUD顶部
```
会将他分割为两个切片（以标题分割）

### 4. 文件上传接口

RESTful API接口，支持知识库文档的批量导入：

```java
@Tag(name = "知识库管理")
@Slf4j
@RestController
@RequestMapping("/file")
public class FileUploadController {

    private final MyAppDocumentLoader documentLoader;
    private final VectorStore vectorStore;

    public FileUploadController(MyAppDocumentLoader documentLoader, VectorStore vectorStore) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
    }

    @Operation(summary = "上传知识库文档", description = "支持Markdown格式文件，自动进行向量化存储")
    @PostMapping("/upload")
    public ResponseEntity<String> uploadMarkdownFile(@RequestPart("file") MultipartFile file) {
        // 文件有效性检查
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("请选择要上传的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".md")) {
            return ResponseEntity.badRequest().body("仅支持Markdown格式文件（.md）");
        }

        try {
            // 解析文档为多个片段
            List<Document> documents = documentLoader.processUploadedFile(file);
            // 自动向量化并存储到数据库
            vectorStore.add(documents);
            log.info("成功处理文件：{}，共生成{}个文档片段", originalFilename, documents.size());
            return ResponseEntity.ok(String.format("文件上传成功！共处理%d个文档片段", documents.size()));
            
        } catch (Exception e) {
            log.error("文件处理失败：{}", originalFilename, e);
            return ResponseEntity.internalServerError()
                    .body("文件处理失败：" + e.getMessage());
        }
    }
}
```
直接使用vectorStore.add()将切片添加到向量库中。
vectorStore会将documents使用embedding模型将切片向量，然后传入向量数据库。这全部都是springAI帮你完成的
这就是SpringAi的强大之处，将大部分操作全帮我们简化了。


### 5. RAG对话服务

集成向量检索的智能问答服务：

```java
@Component
@Slf4j
public class MyApp {
    
    /**
     * 系统提示词，可根据业务需求自定义
     */
    private static final String SYSTEM_PROMPT = """
            你是一个专业的知识库助手。请基于提供的上下文信息回答用户问题。
            如果上下文中没有相关信息，请明确说明无法从知识库中找到答案。
            回答要准确、简洁且有条理。
            """;
    
    private final ChatClient chatClient;
    
    @Resource
    private VectorStore vectorStore;

    public MyApp(ChatModel ollamaChatModel) {
        this.chatClient = ChatClient.builder(ollamaChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /**
     * 基于知识库的智能问答
     * @param message 用户问题
     * @return AI生成的回答
     */
    public String doChat(String message) {
        try {
            ChatResponse chatResponse = chatClient
                    .prompt()
                    .user(message)
                    // 关键：启用RAG增强，自动检索相关文档
                    .advisors(new QuestionAnswerAdvisor(vectorStore))
                    .call()
                    .chatResponse();
            
            String content = chatResponse.getResult().getOutput().getText();
            log.info("用户问题：{}，回答长度：{}", message, content.length());
            return content;
            
        } catch (Exception e) {
            log.error("对话处理失败", e);
            return "抱歉，处理您的问题时出现了错误，请稍后重试。";
        }
    }
}
```
直接使用chatClient.advisors(new QuestionAnswerAdvisor(vectorStore))
启用QuestionAnswerAdvisor查询增强器，将VectorStore实例传入，SpringAI的Advisor会查询向量数据库来获取与用户
问题相关的文档，并将这些文档作为上下文附加到用户的查询中。
这样也是有SpringAI来完成的，我们完全不需要再去自己编写RAG的Prompt，SpringAI都帮我们做完了。
当然，如果有定制化的需求， SpringAI也是支持自己编写RAG的Prompt的。













