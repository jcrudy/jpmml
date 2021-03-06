library("nnet")
library("pmml")
library("randomForest")

ozoneData = readCsv("csv/Ozone.csv")
ozoneFormula = formula(O3 ~ temp + ibh + ibt)

writeOzone = function(values, file){
	result = data.frame(values)
	names(result) = c("O3")

	writeCsv(result, file)
}

generateNeuralNetworkOzone = function(){
	nnet = nnet(ozoneFormula, ozoneData, size = 4, decay = 1e-3, maxit = 10000, linout = TRUE)
	saveXML(pmml(nnet), "pmml/NeuralNetworkOzone.pmml")

	writeOzone(predict(nnet), "csv/NeuralNetworkOzone.csv")
}

generateRandomForestOzone = function(){
	randomForest = randomForest(ozoneFormula, ozoneData, ntree = 10, mtry = 3, nodesize = 10)
	saveXML(pmml(randomForest), "pmml/RandomForestOzone.pmml")

	writeOzone(predict(randomForest, newdata = ozoneData), "csv/RandomForestOzone.csv")
}

generateRegressionOzone = function(){
	lm = lm(ozoneFormula, ozoneData)
	saveXML(pmml(lm), "pmml/RegressionOzone.pmml")
	
	writeOzone(predict(lm), "csv/RegressionOzone.csv")
}

generateNeuralNetworkOzone()
generateRandomForestOzone()
generateRegressionOzone()