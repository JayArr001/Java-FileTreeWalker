import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

//simple one-class program that uses the FileTreeWalker to navigate a directory and print
//contents about a directory, including subdirectory contents and sizing
public class Main
{

	public static void main(String[] args)
	{
		Path startingPath = Path.of(".");//relative path
		System.out.println("current path: " + startingPath.toAbsolutePath());
		FileVisitor<Path> statsVisitor = new Main.StatsVisitor();
		try
		{
			//some of the functions will be overridden
			Files.walkFileTree(startingPath, statsVisitor);
		}
		catch(IOException ioe)
		{
			ioe.printStackTrace();
		}

	}

	//this subclass is for totaling bytes including children
	private static class StatsVisitor extends SimpleFileVisitor<Path>
	{
		private Path initialPath = null;
		private final Map<Path, Long> folderSizes = new LinkedHashMap<>();
		private Map<Path, Integer> folderFileCount = new HashMap<>();
		private Map<Path, Integer> subfolderCount = new HashMap<>();
		private int initialCount;

		public StatsVisitor()
		{}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
		{
			//the default code provided for visitFile();
			Objects.requireNonNull(file);
			Objects.requireNonNull(attributes);

			//adding some code below and overriding the method

			//merge lists, effectively adding subfolders to the parent map
			//the key is the parent of whatever file we're looking at
			//we're accumulating data into the main map, with default 0 if the key (parent) doesn't exist
			//if the key exists (file has a parent), we add the size to the parent's running total
			folderSizes.merge(file.getParent(), 0L,
					(o, n) -> o += attributes.size());
			//this code tallies up file sizes, but totals aren't yet propagated up to parents

			//code for adding up number of files in a directory
			folderFileCount.merge(file.getParent(), 1, (o, n) -> o += 1);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
		{
			//default code for FileTreeWalkerChallenge();
			Objects.requireNonNull(dir);
			Objects.requireNonNull(attrs);

			//adding the following code
			if(initialPath == null)
			{
				initialPath = dir; //initializes our variable to the dir
				initialCount = dir.getNameCount(); //using this trick to get how deep we are
			}
			else //otherwise, we already initialized our path
			{
				//how deep we are relative to the start
				int relativeLevel = dir.getNameCount() - initialCount;
				if(relativeLevel == 1)
				{
					//it is more efficient to keep track of only one folder at a time
					//also lets you print any desired info to the user after each subfolder is worked
					folderSizes.clear();
					folderFileCount.clear();
				}
				//adding/merging the data found to collections
				folderSizes.put(dir, 0L);
				folderFileCount.put(dir, 0);
				subfolderCount.merge(dir.getParent(), 1, (o, n) -> o += 1);
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
		{
			Objects.requireNonNull(dir);

			//if we made it full circle, the walk is over
			if(dir.equals(initialPath))
			{
				return FileVisitResult.TERMINATE;
			}
			Map<Path, String> outputMessage = new HashMap<>();

			int relativeLevel = dir.getNameCount() - initialCount;
			if(relativeLevel == 1) //when it's 1, we're at a level 1 subfolder of initial path
			{
				folderSizes.forEach((key, value) ->
				{
					int level = key.getNameCount() - initialCount - 1;
					String put = "%s[%s] - %,d bytes".formatted("\t".repeat(level), key.getFileName(), value);
					outputMessage.put(key, put);
				});

				folderFileCount.forEach((key, value) ->
				{
					//when we only have one value in the map, it's a file
					//so we only output stuff if we're > 1 elements in our map
					//there's a chance we have an empty folder, so we still need to check if the size == 1
					if(folderFileCount.size() > 1 && key.toFile().isDirectory())
					{
						int level = key.getNameCount() - initialCount - 1;
						if(outputMessage.get(key) != null) //if there is a key/value pairing at the directory
						{
							String oldString = outputMessage.get(key);
							String newString = "%s, %,d files".formatted(oldString, value);
							outputMessage.put(key, newString);
						}
						else //there was no pairing at the directory inspected
						{
							String newString = "%s[%s] - %,d files %n".formatted("\t".repeat(level), key.getFileName(), value);
							outputMessage.put(key, newString);
						}

					}
				});
				//using separate loops for clarity, also is not hugely inefficient (still constant time, 1 loop)
				subfolderCount.forEach((key, value) ->
				{
					if(subfolderCount.size() > 1 && key.toFile().isDirectory())
					{
						int level = key.getNameCount() - initialCount - 1;
						if(level > -1)
						{
							if(outputMessage.get(key) != null)
							{
								String oldString = outputMessage.get(key);
								String newString = "%s, %,d subfolders".formatted(oldString, value);
								outputMessage.put(key, newString);
							}
							else
							{
								String newString = "%s[%s] - %,d subfolders".formatted("\t".repeat(level), key.getFileName(), value);
								outputMessage.put(key, newString);
							}
						}
					}
				});
				outputMessage.forEach((k, s) ->
				{
					if(!s.isEmpty())
					{
						System.out.println(s + " [end]");
					}
				});
			}
			else
			{
				//this should be correct because children have been processed
				long folderSize = folderSizes.get(dir);
				int fileCount = folderFileCount.get(dir);
				if(subfolderCount.get(dir) != null)
				{
					//build collection to hold cumulative subfolder data
					int folderCount = subfolderCount.get(dir);
					subfolderCount.merge(dir.getParent(), 1, (o, n) -> o += folderCount);
				}
				folderSizes.merge(dir.getParent(), 0L, (o, n) -> o += folderSize);
				folderFileCount.merge(dir.getParent(), 0, (o, n) -> o += fileCount);

			}

			return FileVisitResult.CONTINUE;
		}
	}
}
