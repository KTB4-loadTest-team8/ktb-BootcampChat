const getTimestamp = message => new Date(message?.timestamp || 0).getTime();

export const mergeSortedMessageArrays = (currentMessages, incomingMessages) => {
  const merged = [];
  let currentIndex = 0;
  let incomingIndex = 0;

  while (currentIndex < currentMessages.length && incomingIndex < incomingMessages.length) {
    if (getTimestamp(currentMessages[currentIndex]) <= getTimestamp(incomingMessages[incomingIndex])) {
      merged.push(currentMessages[currentIndex]);
      currentIndex += 1;
    } else {
      merged.push(incomingMessages[incomingIndex]);
      incomingIndex += 1;
    }
  }

  return merged.concat(
    currentMessages.slice(currentIndex),
    incomingMessages.slice(incomingIndex)
  );
};

export const deriveUniqueSortedMessages = (
  currentMessages,
  incomingMessages,
  processedMessageIds
) => {
  if (!Array.isArray(incomingMessages)) {
    throw new Error('Invalid messages format');
  }

  const processedSnapshot = new Set(processedMessageIds);
  const nextProcessedMessageIds = new Set(processedMessageIds);
  const newMessages = incomingMessages.filter((message) => {
    if (!message._id) {
      return false;
    }

    if (processedSnapshot.has(message._id)) {
      return false;
    }

    processedSnapshot.add(message._id);
    nextProcessedMessageIds.add(message._id);
    return true;
  });

  const sortedIncomingMessages = [...newMessages].sort(
    (a, b) => getTimestamp(a) - getTimestamp(b)
  );

  return {
    messages: mergeSortedMessageArrays(currentMessages, sortedIncomingMessages),
    processedMessageIds: nextProcessedMessageIds,
  };
};

export const mergeUniqueSortedMessages = (
  currentMessages,
  incomingMessages,
  processedMessageIds
) => {
  return deriveUniqueSortedMessages(
    currentMessages,
    incomingMessages,
    processedMessageIds
  ).messages;
};
