# DSPatch
BSPatch Java implement with android  (based on bspatch by Joe Desbonnet, joe@galway.net (JBPatch))

# 说明

Tinker 源码中存在一份 BSDiff和BSPatch源码，但是其中采用Gzip进行diff和Patch，但是原版的bsdiff文件生成的时候采用bzip算法，此处将该代码使用bzip压缩实现，以用来兼容正常的diff文件
