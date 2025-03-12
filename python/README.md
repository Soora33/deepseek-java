## 1. 补充剩余文件 
剩余 2 个文件需要去官网手动下载：
https://huggingface.co/moka-ai/m3e-base/tree/main
![image-20250311150230554](https://minaseinori.oss-cn-hongkong.aliyuncs.com/%E6%95%99%E5%AD%A6%E7%9B%AE%E5%BD%95/202503111549408.png)

## 2. config.json 配置

- model_path：向量的预训练模型的路径
- index_file：搜索时使用的向量索引的文件路径
- json_data_file：搜索时使用的原始数据的 JSON 文件路径
- faiss_dir：根据原始数据 JSON 生成的 faiss 文件存储路径
- vector_dim：向量的维度大小
- default_top_k：默认情况下返回的最相似结果的数量
- similarity_threshold：相似度阈值（仅返回高于该值的结果）
- server_port：服务端口号
- debug：调试模式
- result_format：
    - include_score：返回的结果中是否包含相似度得分
    - max_content_length：返回结果中内容的最大长度，超出部分会被截断
- data_fields：
    - metadata_fields：原始数据中，除向量字段外的所有字段
    - content_field：原始数据中的向量字段（只能有一个）

model_path 是我们的向量模型路径，这里我用的是 m3e-base 模型，模型小而且效果不错，后面会使用该模型做演示并下载到本地，index_file 和 json_data_file 是我们在做向量查询时使用的文件。其中 index 作为索引，json 作为元数据使用。faiss_dir 则是我们调用 generate-index 接口后生成的索引文件路径。vector_dim 向量维度是跟向量模型本身挂钩的，例如 m3e-base 这个模型的维度就是 768，不可以设置别的值。下面几个参数上面也说的很清楚了。最后一个参数是重点，这个是负责匹配我们知识库元文件字段的，目前大部分源文件格式都是 json，但是字段不可能都一样，所以这里需要配置各自的字段，例如我的元文件字段有四个，需要按照 content 字段做搜索，那么这个字段就是向量字段。